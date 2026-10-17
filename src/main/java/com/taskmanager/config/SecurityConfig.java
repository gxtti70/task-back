package com.taskmanager.config;

import com.taskmanager.security.JwtAuthenticationFilter;
import com.taskmanager.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.beans.factory.annotation.Value;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieService authCookies;

    @Value("${cors.allowed-origins:http://localhost:4300}")
    private String allowedOrigins;
    @Value("${jwt.cookie.secure:false}")
    private boolean cookieSecure;
    @Value("${jwt.cookie.same-site:Lax}")
    private String cookieSameSite;
    @Value("${jwt.cookie.domain:}")
    private String cookieDomain;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. CORS primero
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Cookie authentication is accompanied by a readable double-submit CSRF cookie.
            .csrf(csrf -> {
                CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
                repository.setCookiePath("/");
                repository.setCookieCustomizer(cookie -> {
                    cookie.secure(cookieSecure).sameSite(cookieSameSite).path("/");
                    if (cookieDomain != null && !cookieDomain.isBlank()) cookie.domain(cookieDomain.trim());
                });
                csrf.csrfTokenRepository(repository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .ignoringRequestMatchers("/api/subscriptions/webhook");
            })

            // 3. Manejo de sesiones sin estado
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF failures are explicitly tagged; role denials stay plain 403 and unauthenticated access is 401.
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    String header = request.getHeader("Authorization");
                    if (header == null) {
                        String cookieToken = authCookies.read(request, AuthCookieService.ACCESS_COOKIE);
                        if (cookieToken != null && jwtTokenProvider.validateToken(cookieToken)
                                && jwtTokenProvider.isAccessToken(cookieToken)) {
                            response.sendError(HttpStatus.FORBIDDEN.value());
                            return;
                        }
                    }
                    boolean validAccessToken = false;
                    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                        String token = header.substring(7).trim();
                        validAccessToken = jwtTokenProvider.validateToken(token)
                                && jwtTokenProvider.isAccessToken(token);
                    }
                    response.sendError(validAccessToken ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value());
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    if (accessDeniedException instanceof CsrfException) {
                        response.setHeader("X-Error-Code", "CSRF_INVALID");
                        response.sendError(HttpStatus.FORBIDDEN.value(), "CSRF validation failed");
                    } else {
                        response.sendError(HttpStatus.FORBIDDEN.value());
                    }
                }))

            // 4. Autorizaciones de rutas
            .authorizeHttpRequests(auth -> auth
                // Liberar Preflight OPTIONS para cualquier endpoint
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Aplicar autorización en el filtro para responder 403 en roles insuficientes.
                .requestMatchers(HttpMethod.POST, "/api/tasks", "/api/projects")
                    .hasAnyRole("ADMIN", "SCRUM", "MANAGER")
                .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/subscriptions/webhook").permitAll()
                // Versiones legales vigentes: públicas (se muestran antes de autenticarse).
                .requestMatchers(HttpMethod.GET, "/api/legal/**").permitAll()
                // La activación de planes de desarrollo solo para ADMIN, aunque BILLING_SIMULATE=true.
                .requestMatchers(HttpMethod.POST, "/api/subscriptions/dev-activate/**").hasRole("ADMIN")
                .requestMatchers("/api/auth/**", "/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()

                // Cualquier otra petición requiere JWT
                .anyRequest().authenticated()
            );

        // 5. Filtro de JWT
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permite orígenes locales e hilos de dev de Angular
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept", "X-XSRF-TOKEN"));
        configuration.setExposedHeaders(Collections.singletonList("X-Error-Code"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
