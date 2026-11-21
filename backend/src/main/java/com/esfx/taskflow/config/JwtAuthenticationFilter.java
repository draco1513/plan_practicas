package com.esfx.taskflow.config;

import com.esfx.taskflow.common.AuthUser;
import com.esfx.taskflow.access.UserRecord;
import com.esfx.taskflow.access.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwt, UserRepository users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Optional<Long> id = jwt.parseUserId(header.substring(7).trim());
            if (id.isPresent()) {
                Optional<UserRecord> userOpt = users.findById(id.get());
                if (userOpt.isPresent() && userOpt.get().active()) {
                    UserRecord u = userOpt.get();
                    List<SimpleGrantedAuthority> authorities =
                            users.permissionsOfRole(u.roleId()).stream()
                                    .map(SimpleGrantedAuthority::new)
                                    .toList();

                    var auth = new UsernamePasswordAuthenticationToken(
                            new AuthUser(u.id(), u.email()), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }
}
