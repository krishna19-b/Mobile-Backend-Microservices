package com.krishna.productservice.config;

import com.krishna.productservice.security.InternalServiceAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final InternalServiceAuthFilter internalServiceAuthFilter;

    public SecurityConfig(
            InternalServiceAuthFilter internalServiceAuthFilter) {
        this.internalServiceAuthFilter = internalServiceAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        internalServiceAuthFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth ->
                        auth.anyRequest().authenticated());

        return http.build();
    }
}