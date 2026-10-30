package com.billar.service;

import com.billar.dto.Dtos.*;
import com.billar.model.*;
import com.billar.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/** Gastos, cierre de turno y cálculo de totales. */
@Service
public class CashService {
    private final SaleRepo sales;
    private final ExpenseRepo expenses;
    private final ClosingRepo closings;
    private final SessionRepo sessions;
    private final SettingsService settings;
    private final AuditService audit;

    public CashService(SaleRepo sales, ExpenseRepo expenses, ClosingRepo closings, SessionRepo sessions,
                       SettingsService settings, AuditService audit) {
        this.sales = sales;
        this.expenses = expenses;
        this.closings = closings;
        this.sessions = sessions;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Stats stats(Instant from, Instant to) {
        long cash = 0, transfer = 0, creditNew = 0, courtesy = 0, tv = 0, iv = 0, cost = 0, minutes = 0;
        int count = 0, tableSessions = 0;
        for (Sale s : sales.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to)) {
            if (s.getStatus() == SaleStatus.VOIDED) continue;
            count++;
            switch (s.getMethod()) {
                case CASH -> cash += s.getTotal();
                case TRANSFER -> transfer += s.getTotal();
                case CREDIT -> creditNew += s.getTotal();
                case COURTESY -> courtesy += s.getTotal();
            }
            if (s.getMethod() != PayMethod.COURTESY) {
                tv += s.getTimeValue();
                iv += s.getItemsTotal();
            }
            cost += s.getItemsCost();
            if (s.getType() == SaleType.TABLE) {
                tableSessions++;
                minutes += s.getBilledMinutes();
            }
        }
        // fiados que se cobraron dentro del periodo entran como dinero recibido
        for (Sale s : sales.findByPaidAtBetween(from, to)) {
            if (s.getStatus() == SaleStatus.VOIDED) continue;
            if (s.getPaidMethod() == PayMethod.CASH) cash += s.getTotal(); else transfer += s.getTotal();
        }
        long exp = expenses.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to).stream().mapToLong(Expense::getAmount).sum();
        return new Stats(cash, transfer, creditNew, courtesy, tv, iv, cost, count, tableSessions, minutes, exp);
    }

    public Instant shiftStart() {
        return closings.findFirstByOrderByClosedAtDesc().map(ShiftClosing::getClosedAt).orElse(Instant.EPOCH);
    }

    @Transactional(readOnly = true)
    public ShiftView shift(boolean owner) {
        Instant from = shiftStart(), now = Instant.now().plusSeconds(1);
        Stats st = stats(from, now);
        int open = sessions.findByStatus(SessionStatus.OPEN).size();
        List<ExpenseView> exps = expenses.findByCreatedAtBetweenOrderByCreatedAtDesc(from, now).stream()
                .map(e -> new ExpenseView(e.getId(), e.getConcept(), e.getAmount(), e.getCreatedBy(), e.getCreatedAt())).toList();
        if (!owner) return new ShiftView(from, st.salesCount(), open, exps, null, null, null, null, null, null);
        long base = settings.get().getBaseCash();
        return new ShiftView(from, st.salesCount(), open, exps, base, st.cash(), st.transfer(), st.creditNew(), st.courtesy(),
                base + st.cash() - st.expenses());
    }

    @Transactional
    public ExpenseView addExpense(ExpenseReq r, String user) {
        Expense e = new Expense();
        e.setConcept(r.concept());
        e.setAmount(r.amount());
        e.setCreatedBy(user);
        e.setCreatedAt(Instant.now());
        expenses.save(e);
        audit.log(user, "Gasto: " + r.concept() + " por " + r.amount());
        return new ExpenseView(e.getId(), e.getConcept(), e.getAmount(), e.getCreatedBy(), e.getCreatedAt());
    }

    /** Cierre de turno. El empleado cuenta sin ver el esperado y tampoco recibe la diferencia: solo el dueño. */
    @Transactional
    public ClosingResult close(CloseReq r, String user, boolean owner) {
        Instant from = shiftStart(), now = Instant.now();
        Stats st = stats(from, now.plusSeconds(1));
        long base = settings.get().getBaseCash();
        long expected = base + st.cash() - st.expenses();
        long diff = r.countedCash() - expected;

        ShiftClosing c = new ShiftClosing();
        c.setFromAt(from);
        c.setClosedAt(now);
        c.setClosedBy(user);
        c.setBaseCash(base);
        c.setExpectedCash(expected);
        c.setCountedCash(r.countedCash());
        c.setDifference(diff);
        c.setCashSales(st.cash());
        c.setTransferSales(st.transfer());
        c.setCreditSales(st.creditNew());
        c.setCourtesySales(st.courtesy());
        c.setExpensesTotal(st.expenses());
        closings.save(c);
        audit.log(user, "Cerró turno. Contado " + r.countedCash() + ", esperado " + expected + ", diferencia " + diff);
        return owner ? new ClosingResult("Turno cerrado", diff) : new ClosingResult("Turno cerrado y registrado", null);
    }
}
