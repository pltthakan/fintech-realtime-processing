package com.fintech.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Auth endpoint'leri açık
                .requestMatchers("/api/v1/auth/**").permitAll()
                // Actuator açık
                .requestMatchers("/actuator/**").permitAll()
                // Gateway bu yolu yayınlamaz; servisler Docker ağı üzerinden doğrudan çağırır.
                .requestMatchers("/api/v1/internal/**").permitAll()
                // User endpoint'leri - Gateway JWT filtresinden geçtiği için
                // burada ek güvenlik katmanı gerekmez (gateway hallediyor)
                .requestMatchers("/api/v1/users/**").permitAll()
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
