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

    @Value("${cors.allowed-origins:http://localhost:4200}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 1. CORS primero
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 2. Desactivar CSRF
            .csrf(csrf -> csrf.disable())

            // 3. Manejo de sesiones sin estado
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Sin credenciales es 401; 403 queda reservado para usuarios autenticados sin permiso.
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    String header = request.getHeader("Authorization");
                    boolean validAccessToken = false;
                    if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
                        String token = header.substring(7).trim();
                        validAccessToken = jwtTokenProvider.validateToken(token)
                                && jwtTokenProvider.isAccessToken(token);
                    }
                    response.sendError(validAccessToken ? HttpStatus.FORBIDDEN.value() : HttpStatus.UNAUTHORIZED.value());
                }))

            // 4. Autorizaciones de rutas
            .authorizeHttpRequests(auth -> auth
                // Liberar Preflight OPTIONS para cualquier endpoint
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Aplicar autorización en el filtro para responder 403 en roles insuficientes.
                .requestMatchers(HttpMethod.POST, "/api/tasks", "/api/projects")
                    .hasAnyRole("ADMIN", "SCRUM", "MANAGER")
                .requestMatchers("/api/auth/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
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
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With", "Accept"));
        configuration.setExposedHeaders(Collections.singletonList("Authorization"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
