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
import org.springframework.messaging.Message
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
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import java.security.Key
import java.util.*


@Configuration
class WebMvcConfig : WebMvcConfigurer {
    @Bean
    @Primary
    fun messageSource(): ResourceBundleMessageSource = ResourceBundleMessageSource().apply {
        setDefaultEncoding("UTF-8")
        setDefaultLocale(Locale("uz"))
        setBasename("errors")
    }


    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf(  "https://bitter-bars-bow.loca.lt",
            "http://localhost:3004",
            "https://10b04efca6bb.ngrok-free.app")
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("*")
        configuration.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }

}

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val corsConfigurationSource: CorsConfigurationSource
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .authorizeHttpRequests {
                it.requestMatchers("/ws/**", "/api/auth/login").permitAll()
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}

@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
//            .setAllowedOriginPatterns("*")
            .setAllowedOriginPatterns(
                "https://bitter-bars-bow.loca.lt",
                "http://localhost:3004",
                "https://8317337dbc9f.ngrok-free.app",
            )
            .withSockJS()
    }

    override fun configureMessageBroker(config: MessageBrokerRegistry) {
        config.enableSimpleBroker("/topic", "/queue")
        config.setApplicationDestinationPrefixes("/app")
        config.setUserDestinationPrefix("/user")
    }
}

@Component
class JwtAuthFilter(
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

        val token = authHeader.substring(7)
        val userId = jwtService.extractUserId(token)

        val auth = UsernamePasswordAuthenticationToken(userId, null, listOf())
        SecurityContextHolder.getContext().authentication = auth
        filterChain.doFilter(request, response)
    }
}

@Configuration
class WebSocketSecurityConfig(
    private val jwtService: JwtService
) : WebSocketMessageBrokerConfigurer {

    override fun configureClientInboundChannel(registration: ChannelRegistration) {
        registration.interceptors(object : ChannelInterceptor {
            override fun preSend(
                message: Message<*>,
                channel: MessageChannel
            ): Message<*> {
                val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)

                if (accessor?.command == StompCommand.CONNECT) {
                    val authHeader = accessor.getFirstNativeHeader("Authorization")
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        val token = authHeader.substring(7)
                        val userId = jwtService.extractUserId(token)

                        val authentication = UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            listOf()
                        )

                        accessor.user = authentication
                        SecurityContextHolder.getContext().authentication = authentication
                    }
                }

                return message
            }
        })
    }
}

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
            this["id"] = user.id?.toString()
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