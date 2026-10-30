
package com.example.tenant_config.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            PasswordEncoder passwordEncoder) {

        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("Admin@123"))
                .roles("ADMIN")
                .build();

        UserDetails client = User.builder()
                .username("tenant1")
                .password(passwordEncoder.encode("Tenant@123"))
                .roles("USER")
                .build();
        UserDetails client2 = User.builder()
        .username("tenant2")
        .password(passwordEncoder.encode("Tenant@123"))
        .roles("USER")
        .build();
        return new InMemoryUserDetailsManager(admin, client,client2);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            .authorizeHttpRequests(auth -> auth

                // Public health endpoint
                .requestMatchers("/actuator/health").permitAll()

                // Read-only APIs: ADMIN and USER
                .requestMatchers(HttpMethod.GET,
                        "/api/configs/resolve",
                        "/api/configs/explain",
                        "/api/configs/history",
                        "/api/configs/version",
                        "/api/configs/events")
                        .hasAnyRole("ADMIN", "USER")

                // Configuration management: ADMIN only
                .requestMatchers(HttpMethod.POST,
                        "/api/configs",
                        "/api/configs/overrides",
                        "/api/configs/change-set",
                        "/api/configs/staged-rollout",
                        "/api/configs/rollback")
                        .hasRole("ADMIN")

                // All remaining API requests require ADMIN
                .requestMatchers("/api/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )

            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
