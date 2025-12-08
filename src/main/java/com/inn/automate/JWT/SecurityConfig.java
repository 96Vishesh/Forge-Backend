package com.inn.automate.JWT;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomerUsersDetailService customerUsersDetailsService;
    private final JwtFilter jwtFilter; // Constructor-based injection

    public SecurityConfig(CustomerUsersDetailService customerUsersDetailsService, JwtFilter jwtFilter) {
        this.customerUsersDetailsService = customerUsersDetailsService;
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    // ✅ Fixed: Use BCryptPasswordEncoder for hashing passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for API endpoints
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/scraper/**").permitAll() // Allow scraper endpoints
                        // Granting public access to all Gemini Resume endpoints
                        .requestMatchers("/resume/**").permitAll()
                        .requestMatchers("/resume/health").permitAll()
                        .requestMatchers("/resume/transform").permitAll()
                        .requestMatchers("/resume/download").permitAll()
                        .requestMatchers("/jobs/match").permitAll()
                        .requestMatchers("/jobs/health").permitAll()
                        .requestMatchers("/resume/generate-pdf").permitAll()
                        .requestMatchers("/resume/templates").permitAll()
                        .requestMatchers("/resume/upload").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/user/login", "/user/signup", "/user/forgotPassword").permitAll() // Allow error page
                        .anyRequest().authenticated()

                );

        return http.build();
    }
}
