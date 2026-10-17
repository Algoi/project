package com.community.meal.auth;

import com.community.meal.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableScheduling
public class SecurityConfig {
  @Bean
  PasswordEncoder passwordEncoder(@Value("${app.auth.password-strength}") int strength) {
    return new BCryptPasswordEncoder(strength);
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, TokenService tokens, UserMapper users, ObjectMapper json)
      throws Exception {
    return http
        // Only explicit Authorization headers authenticate requests. No auth cookies or HTTP
        // sessions.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .headers(headers -> headers.cacheControl(cache -> {}))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/login")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .exceptionHandling(
            errors ->
                errors
                    .authenticationEntryPoint(
                        (request, response, error) -> {
                          response.setStatus(401);
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(
                              response.getOutputStream(),
                              ApiResponse.error("UNAUTHENTICATED", "请重新登录"));
                        })
                    .accessDeniedHandler(
                        (request, response, error) -> {
                          response.setStatus(403);
                          response.setContentType("application/json;charset=UTF-8");
                          json.writeValue(
                              response.getOutputStream(),
                              ApiResponse.error("FORBIDDEN", "无权访问该资源"));
                        }))
        .addFilterBefore(
            new BearerTokenFilter(tokens, users, json), UsernamePasswordAuthenticationFilter.class)
        .build();
  }
}
