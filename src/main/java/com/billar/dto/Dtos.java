package com.billar.dto;

import com.billar.model.*;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Objetos que entran y salen por la API. Los valores de dinero son pesos colombianos enteros. */
public final class Dtos {
    private Dtos() {}

    // ----- entrada -----
    public record LoginReq(@NotBlank String username, @NotBlank String password) {}
    public record TableReq(@NotBlank String name, @Positive long ratePerHour) {}
    public record ProductReq(@NotBlank String name, @PositiveOrZero long price, @PositiveOrZero long cost, @PositiveOrZero int initialStock) {}
    public record PurchaseReq(@Positive int qty) {}
    public record StartReq(@NotNull Long tableId) {}
    public record TabReq(@NotBlank String label) {}
    public record ItemReq(@NotNull Long productId) {}
    public record ChargeReq(@NotNull PayMethod method, String customerName, String note) {}
    public record QuickItem(@NotNull Long productId, @Positive int qty) {}
    public record QuickSaleReq(@NotEmpty List<QuickItem> items, @NotNull PayMethod method, String customerName, String note) {}
    public record PayReq(@NotNull PayMethod method) {}
    public record ExpenseReq(@NotBlank String concept, @Positive long amount) {}
    public record CloseReq(@PositiveOrZero long countedCash) {}
    public record CountLine(@NotNull Long productId, @PositiveOrZero int counted) {}
    public record CountReq(@NotEmpty List<CountLine> lines) {}
    public record UserReq(@NotBlank String username, @NotBlank String fullName, @Size(min = 8) String password, @NotNull Role role) {}
    public record PasswordReq(@Size(min = 8) String password) {}
    public record SettingsReq(@NotBlank String businessName, @Min(1) int billingStepMinutes, @PositiveOrZero long baseCash) {}

    // ----- salida -----
    public record LoginRes(String token, String username, String fullName, Role role) {}
    public record UserView(Long id, String username, String fullName, Role role, boolean active) {}
    public record TableView(Long id, String name, long ratePerHour) {}
    /** cost solo se llena para el dueño. */
    public record ProductView(Long id, String name, long price, Long cost, int stock) {}
    public record ItemView(Long productId, String name, long price, int qty) {}
    public record SessionView(Long id, Long tableId, String tableName, Long ratePerHour, Instant startedAt,
                              Instant serverNow, List<ItemView> items, long itemsTotal, long timeValueSoFar) {}
    public record SaleItemView(String name, long price, int qty) {}
    public record SaleView(Long id, SaleType type, String tableName, Instant startedAt, Instant endedAt, int billedMinutes,
                           long timeValue, long itemsTotal, long total, PayMethod method, String customerName, String note,
                           String createdBy, Instant createdAt, SaleStatus status, boolean paid, List<SaleItemView> items) {
        public static SaleView of(Sale s) {
            return new SaleView(s.getId(), s.getType(), s.getTableName(), s.getStartedAt(), s.getEndedAt(), s.getBilledMinutes(),
                    s.getTimeValue(), s.getItemsTotal(), s.getTotal(), s.getMethod(), s.getCustomerName(), s.getNote(),
                    s.getCreatedBy(), s.getCreatedAt(), s.getStatus(), s.isPaid(),
                    s.getItems().stream().map(i -> new SaleItemView(i.getName(), i.getPrice(), i.getQty())).toList());
        }
    }
    public record ExpenseView(Long id, String concept, long amount, String createdBy, Instant createdAt) {}
    public record Stats(long cash, long transfer, long creditNew, long courtesy, long tableValue, long itemsValue,
                        long itemsCost, int salesCount, int tableSessions, long minutes, long expenses) {}
    /** Para el empleado solo se llenan from, salesCount, openTables y expenses (cierre ciego). */
    public record ShiftView(Instant from, int salesCount, int openTables, List<ExpenseView> expenses,
                            Long baseCash, Long cash, Long transfer, Long creditNew, Long courtesy, Long expectedCash) {}
    public record ClosingResult(String message, Long difference) {}
    public record ClosingView(Long id, Instant closedAt, String closedBy, long expectedCash, long countedCash, long difference) {}
    public record DayPoint(LocalDate date, long income) {}
    public record Summary(Stats stats, long income, long profit, List<ClosingView> cashDifferences,
                          long inventoryDifferenceAtCost, int voidedSales, int shortSessions,
                          long creditPendingTotal, List<SaleView> creditPending, List<DayPoint> daily) {}
    public record AuditView(Instant at, String username, String action) {}
    public record SettingsView(String businessName, int billingStepMinutes, long baseCash) {}
}
