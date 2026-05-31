package com.labelhub.api.config;

import com.labelhub.api.security.InternalTokenFilter;
import com.labelhub.api.security.JwtAuthenticationFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        InternalTokenFilter internalTokenFilter,
        JwtAuthenticationFilter jwtAuthenticationFilter
    ) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/auth/**",
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/metrics",
                    "/actuator/prometheus",
                    "/error"
                ).permitAll()
                .requestMatchers("/internal/**").permitAll()
                .requestMatchers("/schemas/**").hasRole("OWNER")
                .requestMatchers("/datasets/**").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/tasks/*/submissions").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/tasks/marketplace").hasRole("LABELER")
                .requestMatchers(HttpMethod.POST, "/tasks/*/claim").hasRole("LABELER")
                .requestMatchers("/sessions/**").hasRole("LABELER")
                .requestMatchers("/my/sessions").hasRole("LABELER")
                .requestMatchers(HttpMethod.POST, "/submissions/*/ai-review").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/submissions/*/ai-review").authenticated()
                .requestMatchers(HttpMethod.GET, "/prompt-versions/default").authenticated()
                .requestMatchers(HttpMethod.GET, "/reviewer/submissions").hasRole("REVIEWER")
                .requestMatchers(HttpMethod.POST, "/reviews/batch").hasRole("REVIEWER")
                .requestMatchers(HttpMethod.POST, "/submissions/*/ledger-entries").hasRole("REVIEWER")
                .requestMatchers(HttpMethod.GET, "/submissions/*/ledger-entries").authenticated()
                .requestMatchers(HttpMethod.GET, "/submissions/*/verdict").authenticated()
                .requestMatchers(HttpMethod.POST, "/tasks/*/exports").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/tasks/*/exports").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/exports/snapshots/*/diff").hasRole("OWNER")
                .requestMatchers(HttpMethod.GET, "/exports/snapshots/*").hasRole("OWNER")
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(internalTokenFilter, JwtAuthenticationFilter.class)
            .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // bcrypt cost 10 matches seed migration; raise to 12 if production deployment.
        return new BCryptPasswordEncoder(10);
    }
}
