package com.community.meal.auth;

import com.community.meal.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

// Registered only inside SecurityFilterChain, never as a servlet component.
public class BearerTokenFilter extends OncePerRequestFilter {
  private final TokenService tokens;
  private final UserMapper users;
  private final ObjectMapper json;

  public BearerTokenFilter(TokenService tokens, UserMapper users, ObjectMapper json) {
    this.tokens = tokens;
    this.users = users;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getServletPath().equals("/api/v1/auth/login");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null) {
      String raw = header.startsWith("Bearer ") ? header.substring(7) : null;
      AppUser user = tokens.resolve(raw);
      var roles = user == null ? java.util.List.<String>of() : users.roles(user.getId());
      if (user == null || roles.isEmpty()) {
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.error("UNAUTHENTICATED", "请重新登录"));
        return;
      }
      var principal =
          new AuthPrincipal(user.getId(), user.getUsername(), roles, TokenService.hash(raw));
      var authorities =
          roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
      var context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(
          new UsernamePasswordAuthenticationToken(principal, null, authorities));
      SecurityContextHolder.setContext(context);
    }
    chain.doFilter(request, response);
  }
}
