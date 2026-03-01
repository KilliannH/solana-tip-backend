package com.solanatip.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    @Value("${app.base-url:https://solana-tip.com}")
    private String baseUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // IF_REQUIRED allows sessions for OAuth2 flow (state parameter storage)
                // JWT is still used for API auth via JwtAuthFilter
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        // Auth endpoints — public
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // OAuth2 endpoints — public
                        .requestMatchers("/api/v1/auth/oauth2/**").permitAll()

                        // Creators — GET is public, write operations require auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/creators/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/creators/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/creators/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/creators/**").authenticated()

                        // Tips — all public
                        .requestMatchers("/api/v1/tips/**").permitAll()

                        // Activity feed — public
                        .requestMatchers("/api/v1/activity/**").permitAll()

                        // Stripe webhook — public (Stripe calls this)
                        .requestMatchers("/api/v1/subscription/webhook").permitAll()

                        // Subscription management — requires auth (except webhook above)
                        .requestMatchers("/api/v1/subscription/**").authenticated()

                        // Alerts SSE — public (OBS overlay), but PUT requires auth
                        .requestMatchers(HttpMethod.PUT, "/api/v1/alerts/**").authenticated()
                        .requestMatchers("/api/v1/alerts/**").permitAll()

                        // Sitemap
                        .requestMatchers("/sitemap.xml").permitAll()

                        // Admin — requires auth
                        .requestMatchers("/api/v1/admin/**").authenticated()

                        // Everything else requires auth
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(auth -> auth
                                .baseUri("/api/v1/auth/oauth2")
                        )
                        .redirectionEndpoint(redirect -> redirect
                                .baseUri("/api/v1/auth/oauth2/callback/*")
                        )
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler((req, res, ex) -> {
                            res.sendRedirect(baseUrl + "/auth?error=oauth_failed");
                        })
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}