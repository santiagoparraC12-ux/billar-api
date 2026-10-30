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
public class CashController {
    private final CashService cash;

    public CashController(CashService cash) { this.cash = cash; }

    private static boolean owner(Authentication a) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_OWNER"));
    }

    @GetMapping("/cash/shift")
    public ShiftView shift(Authentication auth) { return cash.shift(owner(auth)); }

    @PostMapping("/cash/close")
    public ClosingResult close(@Valid @RequestBody CloseReq r, Authentication auth) {
        return cash.close(r, auth.getName(), owner(auth));
    }

    @PostMapping("/expenses")
    public ExpenseView expense(@Valid @RequestBody ExpenseReq r, Authentication auth) { return cash.addExpense(r, auth.getName()); }
}
