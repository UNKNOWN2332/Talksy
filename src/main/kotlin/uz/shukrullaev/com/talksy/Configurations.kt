package uz.shukrullaev.com.talksy

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Service
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration
import org.springframework.web.socket.server.HandshakeInterceptor
import org.springframework.web.socket.server.support.DefaultHandshakeHandler
import java.security.Key
import java.security.Principal
import java.util.*

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

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://192.168.0.1:3000",
                "https://93a8c02c76fb.ngrok-free.app",
                "https://contracts-demo.netlify.app",
                "http://192.168.0.161:3004",
                "http://localhost:3004",
                "https://8d6cf68ed0a4.ngrok-free.app",
                "https://tender-dryers-exist.loca.lt"
            )
            .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600)
    }
}


@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/", "/index.html", "/favicon.ico",
                        "/css/**", "/js/**", "/images/**",
                        "/api/auth/**", "/api/auth/login",
                        "/ws/**"
                    ).permitAll()
                    .anyRequest().authenticated()
            }
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
class WebSocketConfig(private val jwtService: JwtService) : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic", "/queue")
        registry.setUserDestinationPrefix("/user")
        registry.setApplicationDestinationPrefixes("/app")

    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws")
            .setAllowedOrigins(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://192.168.0.1:3000",
                "https://93a8c02c76fb.ngrok-free.app",
                "https://contracts-demo.netlify.app",
                "http://192.168.0.161:3004",
                "http://localhost:3004",
                "https://8d6cf68ed0a4.ngrok-free.app",
                "https://vast-days-dream.loca.lt",
                "https://tender-dryers-exist.loca.lt"
            )
            .addInterceptors(JwtHandshakeInterceptor(jwtService))
            .setHandshakeHandler(JwtHandshakeHandler())
            .withSockJS()
    }

    override fun configureWebSocketTransport(registry: WebSocketTransportRegistration) {
        val corsConfig = CorsConfiguration().apply {
            allowedOrigins = listOf(
                "http://localhost:3000",
                "http://localhost:3001",
                "http://192.168.0.1:3000",
                "https://93a8c02c76fb.ngrok-free.app",
                "https://contracts-demo.netlify.app",
                "http://192.168.0.161:3004",
                "http://localhost:3004",
                "https://8d6cf68ed0a4.ngrok-free.app",
                "https://tender-dryers-exist.loca.lt"
            )
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        registry.setMessageSizeLimit(512 * 1024)
    }
}

class JwtHandshakeHandler : DefaultHandshakeHandler() {
    override fun determineUser(
        request: ServerHttpRequest,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Principal {
        val userId = attributes["userId"] as? String ?: "anonymous"
        return UsernamePasswordAuthenticationToken(userId, null)
    }
}

class JwtHandshakeInterceptor(
    private val jwtService: JwtService
) : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>
    ): Boolean {
        val authHeader = request.headers.getFirst("Authorization")

        if (!authHeader.isNullOrBlank() && authHeader.startsWith("Bearer ")) {
            val token = authHeader.removePrefix("Bearer ").trim()
            val userId = jwtService.extractUserId(token)
            attributes["userId"] = userId
            return true
        }

        response.setStatusCode(HttpStatus.FORBIDDEN)
        return false
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?
    ) {
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