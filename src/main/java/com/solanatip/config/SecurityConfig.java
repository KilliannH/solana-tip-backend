package com.solanatip.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints — public
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Creators — GET is public, write operations require auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/creators/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/creators/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/creators/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/creators/**").authenticated()

                        // Tips — all public (anyone can tip, anyone can see history)
                        .requestMatchers("/api/v1/tips/**").permitAll()

                        // Admin — requires auth (admin check done in controller)
                        .requestMatchers("/api/v1/admin/**").authenticated()

                        // Everything else requires auth
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}