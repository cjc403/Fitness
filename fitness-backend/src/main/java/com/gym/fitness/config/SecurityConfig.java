package com.gym.fitness.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gym.fitness.common.constant.ErrorCode;
import com.gym.fitness.common.response.Result;
import com.gym.fitness.security.JwtAuthenticationFilter;
import com.gym.fitness.security.JwtTokenProvider;
import com.gym.fitness.security.PasswordChangeRequiredFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final ObjectMapper objectMapper;
    private final com.gym.fitness.module.agent.task.AgentMutationFilter agentMutationFilter;

    @Bean
    public org.springframework.boot.web.servlet.FilterRegistrationBean<com.gym.fitness.module.agent.task.AgentMutationFilter> agentMutationRegistration(){
        var registration=new org.springframework.boot.web.servlet.FilterRegistrationBean<>(agentMutationFilter);registration.setEnabled(false);return registration;
    }
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    Object authError = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
                    ErrorCode errorCode = authError == JwtTokenProvider.TokenValidationStatus.EXPIRED
                            ? ErrorCode.TOKEN_EXPIRED
                            : authError == JwtTokenProvider.TokenValidationStatus.INVALID
                            ? ErrorCode.TOKEN_INVALID
                            : ErrorCode.UNAUTHORIZED;
                    writeSecurityError(response, HttpServletResponse.SC_UNAUTHORIZED, errorCode);
                })
                .accessDeniedHandler((request, response, exception) ->
                    writeSecurityError(response, HttpServletResponse.SC_FORBIDDEN, ErrorCode.FORBIDDEN))
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(passwordChangeRequiredFilter, JwtAuthenticationFilter.class)
            .addFilterAfter(agentMutationFilter, PasswordChangeRequiredFilter.class);
        
        return http.build();
    }

    private void writeSecurityError(HttpServletResponse response, int status, ErrorCode errorCode) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(errorCode));
    }
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
