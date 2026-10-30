package com.billar.service;

import com.billar.dto.Dtos.*;
import com.billar.error.ApiException;
import com.billar.model.AppUser;
import com.billar.repo.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepo users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepo users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return users.findAll().stream().map(this::view).toList();
    }

    @Transactional
    public UserView create(UserReq r, String by) {
        if (r.password() == null) throw ApiException.badRequest("La contraseña debe tener mínimo 8 caracteres");
        if (users.existsByUsername(r.username())) throw ApiException.conflict("Ese usuario ya existe");
        AppUser u = new AppUser();
        u.setUsername(r.username());
        u.setFullName(r.fullName());
        u.setRole(r.role());
        u.setPasswordHash(encoder.encode(r.password()));
        users.save(u);
        audit.log(by, "Creó usuario " + r.username() + " (" + r.role() + ")");
        return view(u);
    }

    @Transactional
    public UserView setActive(Long id, boolean active, String by) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("El usuario no existe"));
        if (u.getUsername().equals(by)) throw ApiException.conflict("No puedes desactivarte a ti mismo");
        u.setActive(active);
        audit.log(by, (active ? "Activó" : "Desactivó") + " al usuario " + u.getUsername());
        return view(u);
    }

    @Transactional
    public void resetPassword(Long id, String password, String by) {
        if (password == null) throw ApiException.badRequest("La contraseña debe tener mínimo 8 caracteres");
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("El usuario no existe"));
        u.setPasswordHash(encoder.encode(password));
        audit.log(by, "Cambió la contraseña de " + u.getUsername());
    }

    private UserView view(AppUser u) {
        return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getRole(), u.isActive());
    }
}
