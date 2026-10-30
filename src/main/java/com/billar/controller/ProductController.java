package com.billar.controller;

import com.billar.dto.Dtos.*;
import com.billar.service.*;
import com.billar.repo.*;
import com.billar.model.*;
import com.billar.error.ApiException;
import com.billar.security.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final InventoryService inv;

    public ProductController(InventoryService inv) { this.inv = inv; }

    private static boolean owner(Authentication a) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_OWNER"));
    }

    @GetMapping("/products")
    public List<ProductView> list(Authentication auth) { return inv.list(owner(auth)); }

    @PostMapping("/products")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView create(@Valid @RequestBody ProductReq r, Authentication auth) { return inv.create(r, auth.getName()); }

    @PutMapping("/products/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView update(@PathVariable Long id, @Valid @RequestBody ProductReq r, Authentication auth) {
        return inv.update(id, r, auth.getName());
    }

    @DeleteMapping("/products/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public void delete(@PathVariable Long id, Authentication auth) { inv.delete(id, auth.getName()); }

    @PostMapping("/products/{id}/purchase")
    @PreAuthorize("hasRole('OWNER')")
    public ProductView purchase(@PathVariable Long id, @Valid @RequestBody PurchaseReq r, Authentication auth) {
        return inv.purchase(id, r.qty(), auth.getName());
    }

    @PostMapping("/inventory/count")
    public Map<String, Integer> count(@Valid @RequestBody CountReq r, Authentication auth) {
        return Map.of("differences", inv.count(r, auth.getName()));
    }
}
