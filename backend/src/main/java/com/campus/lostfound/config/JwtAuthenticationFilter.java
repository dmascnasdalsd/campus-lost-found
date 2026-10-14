package com.campus.lostfound.config;

import com.campus.lostfound.service.JwtService;
import com.campus.lostfound.service.TokenBlacklist;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 从 Authorization: Bearer &lt;token&gt; 解析 JWT，校验通过后写入 SecurityContext。
 * principal 为用户 id（Long），权限为 ROLE_&lt;角色&gt;。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtService jwtService, TokenBlacklist tokenBlacklist) {
        this.jwtService = jwtService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && !tokenBlacklist.isRevoked(token)) {
            authenticate(token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token) {
        try {
            JwtService.ParsedToken parsed = jwtService.parse(token);
            List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + parsed.role().name()));
            var authentication = new UsernamePasswordAuthenticationToken(parsed.userId(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            // token 非法或过期：保持未认证状态，交由 EntryPoint 返回统一 401。
            SecurityContextHolder.clearContext();
        }
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
