package com.billar.security;

import com.billar.model.AppUser;
import com.billar.repo.UserRepo;
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

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepo users;

    public JwtAuthFilter(JwtService jwt, UserRepo users) {
        this.jwt = jwt;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String h = req.getHeader("Authorization");
        if (h != null && h.startsWith("Bearer ")) {
            try {
                String username = jwt.parse(h.substring(7)).getSubject();
                // Se consulta la base en cada petición: si el dueño desactiva a un empleado, pierde acceso de inmediato.
                AppUser u = users.findByUsername(username).orElse(null);
                if (u != null && u.isActive()) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            u.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (Exception ignored) {
                // token inválido o vencido: la petición sigue sin autenticar y recibirá 401
            }
        }
        chain.doFilter(req, res);
    }
}
