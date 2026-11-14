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
public class PosController {
    private final PosService pos;
    private final ZoneId zone;

    public PosController(PosService pos, ZoneId zone) {
        this.pos = pos;
        this.zone = zone;
    }

    @GetMapping("/sessions")
    public List<SessionView> open() { return pos.openSessions(); }

    @PostMapping("/sessions")
    public SessionView start(@Valid @RequestBody StartReq r, Authentication auth) { return pos.start(r.tableId(), auth.getName()); }

    @PostMapping("/tabs")
    public SessionView openTab(@Valid @RequestBody TabReq r, Authentication auth) { return pos.openTab(r.label(), auth.getName()); }

    @PostMapping("/sessions/{id}/items")
    public SessionView add(@PathVariable Long id, @Valid @RequestBody ItemReq r, Authentication auth) {
        return pos.addItem(id, r.productId(), r.playerId(), auth.getName());
    }

    @DeleteMapping("/sessions/{id}/items/{productId}")
    public SessionView remove(@PathVariable Long id, @PathVariable Long productId,
                              @RequestParam(required = false) Long playerId, Authentication auth) {
        return pos.removeItem(id, productId, playerId, auth.getName());
    }

    @PostMapping("/sessions/{id}/charge")
    public SaleView charge(@PathVariable Long id, @Valid @RequestBody ChargeReq r, Authentication auth) {
        return pos.charge(id, r, auth.getName());
    }

    @PostMapping("/sessions/{id}/players")
    public SessionView addPlayer(@PathVariable Long id, @Valid @RequestBody PlayerReq r, Authentication auth) {
        return pos.addPlayer(id, r.name(), auth.getName());
    }

    @PostMapping("/sessions/{id}/series/start")
    public SessionView startSeries(@PathVariable Long id, Authentication auth) { return pos.startSeries(id, auth.getName()); }

    @PostMapping("/sessions/{id}/series/end")
    public SessionView endSeries(@PathVariable Long id, @Valid @RequestBody LoserReq r, Authentication auth) {
        return pos.endSeries(id, r.loserId(), auth.getName());
    }

    @PostMapping("/sessions/{id}/charge-players")
    public List<SaleView> chargePlayers(@PathVariable Long id, @Valid @RequestBody PlayersChargeReq r, Authentication auth) {
        return pos.chargePlayers(id, r, auth.getName());
    }

    @PutMapping("/sessions/{id}/items/move")
    public SessionView moveItem(@PathVariable Long id, @Valid @RequestBody MoveReq r, Authentication auth) {
        return pos.moveItem(id, r, auth.getName());
    }

    @PostMapping("/sales/quick")
    public SaleView quick(@Valid @RequestBody QuickSaleReq r, Authentication auth) { return pos.quickSale(r, auth.getName()); }

    @GetMapping("/sales")
    @PreAuthorize("hasRole('OWNER')")
    public List<SaleView> sales(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return pos.salesBetween(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    @PostMapping("/sales/{id}/void")
    @PreAuthorize("hasRole('OWNER')")
    public SaleView voidSale(@PathVariable Long id, Authentication auth) { return pos.voidSale(id, auth.getName()); }

    @PostMapping("/sales/{id}/pay")
    @PreAuthorize("hasRole('OWNER')")
    public SaleView pay(@PathVariable Long id, @Valid @RequestBody PayReq r, Authentication auth) {
        return pos.payCredit(id, r.method(), auth.getName());
    }
}
