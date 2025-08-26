package uz.shukrullaev.com.talksy

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.config.ChannelRegistration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.nio.charset.StandardCharsets
import java.security.Key
import java.util.*
import javax.crypto.spec.SecretKeySpec
import org.springframework.messaging.Message
import java.security.Principal


/**
 * @see uz.shukrullaev.com.talksy
 * @author Abdulloh
 * @since 18/08/2025 6:08 pm
 */

@Configuration
class WebMvcConfig : WebMvcConfigurer {

    @Bean
    @Primary
    fun messageSource(): ResourceBundleMessageSource {
        return ResourceBundleMessageSource().apply {
            setDefaultEncoding("UTF-8")
            setDefaultLocale(Locale("uz"))
            setBasename("errors")
        }
    }

//    override fun addCorsMappings(registry: CorsRegistry) {
//        registry.addMapping("/**")
//            .allowedOrigins(
//                "http://localhost:3000",
//                "http://localhost:3001",
//                "http://192.168.0.1:3000",
//                "http://localhost:3004",
//                "https://42946bfca812.ngrok-free.app",
//                "https://talksy-m6ja.onrender.com"
//            )
//            .allowedMethods("*")
//            .allowedHeaders("*")
//            .allowCredentials(true)
//    }

}

@Configuration
class JwtDecoderConfig(
    @Value("\${jwt.secret}") private val secret: String
) {

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        return NimbusJwtDecoder.withSecretKey(key).build()
    }
}


@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/", "/index.html", "/favicon.ico",
                        "/css/**", "/js/**", "/images/**", "/home.html", "/chat.html",
                        "/api/auth/**",   // bu o‘zi yetadi, login ham ichida
                        "/ws/info/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun webSecurityCustomizer(): WebSecurityCustomizer {
        return WebSecurityCustomizer { web ->
            web.ignoring().requestMatchers("/favicon.ico")
        }
    }
}

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    private val authChannelInterceptor: AuthChannelInterceptor
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOriginPatterns("*")
            .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setApplicationDestinationPrefixes("/app")
        registry.setUserDestinationPrefix("/user")
    }

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(authChannelInterceptor)
    }
}

@Component
class AuthChannelInterceptor(
    private val jwtService: JwtService
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*>? {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
            ?: return message

        if (StompCommand.CONNECT == accessor.command) {
            val authHeader = accessor.getFirstNativeHeader("Authorization")
            if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
                throw IllegalArgumentException("Missing or invalid Authorization header")
            }
            val token = authHeader.removePrefix("Bearer ").trim()
            val userId = jwtService.extractUserId(token)

            accessor.user = StompPrincipal(userId.toString())
        }
        return message
    }
}
class StompPrincipal(
    private val name: String
) : Principal {
    override fun getName(): String = name
}

//class JwtHandshakeHandler : DefaultHandshakeHandler() {
//    override fun determineUser(
//        request: ServerHttpRequest,
//        wsHandler: WebSocketHandler,
//        attributes: MutableMap<String, Any>
//    ): Principal {
//        val userId = attributes["userId"] as? String ?: "anonymous"
//        return UsernamePasswordAuthenticationToken(userId, null)
//    }
//}
//
//class JwtHandshakeInterceptor(
//    private val jwtService: JwtService
//) : HandshakeInterceptor {
//
//    override fun beforeHandshake(
//        request: ServerHttpRequest,
//        response: ServerHttpResponse,
//        wsHandler: WebSocketHandler,
//        attributes: MutableMap<String, Any>
//    ): Boolean {
//        val authHeader = request.headers.getFirst("Authorization")
//        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
//            response.setStatusCode(HttpStatus.UNAUTHORIZED)
//            return false
//        }
//
//        try {
//            val token = authHeader.removePrefix("Bearer ").trim()
//            val userId = jwtService.extractUserId(token)
//            attributes["userId"] = userId
//            // Optionally set authentication for WebSocket context
//            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
//            return true
//        } catch (e: Exception) {
//            response.setStatusCode(HttpStatus.UNAUTHORIZED)
//            return false
//        }
//    }
//
//    override fun afterHandshake(
//        request: ServerHttpRequest,
//        response: ServerHttpResponse,
//        wsHandler: WebSocketHandler,
//        exception: Exception?
//    ) {
//    }
//}

@Service
class JwtService(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration}") private val expiration: Long
) {

    private val key: Key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateToken(user: User): TokenDTO {
        val now = Date()
        val expiry = Date(now.time + expiration)
        val claims = Jwts.claims().apply {
            subject = user.username
            this["id"] = user.id.toString()
            this["telegramId"] = user.telegramId
        }

        val token = Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()

        return TokenDTO(token, now, expiry)
    }

    fun extractUserId(token: String): String {
        val claims = extractAllClaims(token)
        return claims["telegramId"] as String
    }

    private fun extractAllClaims(token: String): Claims {
        return Jwts.parserBuilder()
            .setSigningKey(key)
            .build()
            .parseClaimsJws(token)
            .body
    }
}


@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")

        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val jwt = authHeader.removePrefix("Bearer ").trim()
            val userId = jwtService.extractUserId(jwt)

            if (SecurityContextHolder.getContext().authentication == null) {
                val authentication = UsernamePasswordAuthenticationToken(
                    userId, null, emptyList()
                )
                authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                SecurityContextHolder.getContext().authentication = authentication
            }

            filterChain.doFilter(request, response)
        } catch (e: Exception) {
            logger.error("JWT validation failed: ${e.message}")
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            return
        }
    }
}

