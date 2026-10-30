package com.billar.service;

import com.billar.dto.Dtos.*;
import com.billar.model.*;
import com.billar.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportService {
    private final CashService cash;
    private final SaleRepo sales;
    private final ClosingRepo closings;
    private final CountRepo counts;
    private final ZoneId zone;

    public ReportService(CashService cash, SaleRepo sales, ClosingRepo closings, CountRepo counts, ZoneId zone) {
        this.cash = cash;
        this.sales = sales;
        this.closings = closings;
        this.counts = counts;
        this.zone = zone;
    }

    @Transactional(readOnly = true)
    public Summary summary(LocalDate fromDay, LocalDate toDay) {
        Instant a = fromDay.atStartOfDay(zone).toInstant();
        Instant b = toDay.plusDays(1).atStartOfDay(zone).toInstant();
        Stats st = cash.stats(a, b);
        long income = st.tableValue() + st.itemsValue();
        long profit = income - st.itemsCost() - st.expenses();

        List<ClosingView> diffs = closings.findByClosedAtBetweenOrderByClosedAtDesc(a, b).stream()
                .filter(c -> c.getDifference() != 0)
                .map(c -> new ClosingView(c.getId(), c.getClosedAt(), c.getClosedBy(), c.getExpectedCash(), c.getCountedCash(), c.getDifference()))
                .toList();

        long invDiff = counts.findByCountedAtBetween(a, b).stream().mapToLong(c -> (long) c.getDifference() * c.getUnitCost()).sum();

        List<Sale> all = sales.findByCreatedAtBetweenOrderByCreatedAtDesc(a, b);
        int voided = (int) all.stream().filter(s -> s.getStatus() == SaleStatus.VOIDED).count();
        int shortSessions = (int) all.stream().filter(s -> s.getStatus() == SaleStatus.ACTIVE
                && s.getType() == SaleType.TABLE && s.getActualSeconds() < 300).count();

        List<SaleView> pending = sales.findByMethodAndPaidFalseAndStatusOrderByCreatedAtAsc(PayMethod.CREDIT, SaleStatus.ACTIVE)
                .stream().map(SaleView::of).toList();
        long pendingTotal = pending.stream().mapToLong(SaleView::total).sum();

        List<DayPoint> daily = new ArrayList<>();
        LocalDate d = fromDay;
        int guard = 0;
        while (!d.isAfter(toDay) && guard++ < 62) {
            Stats ds = cash.stats(d.atStartOfDay(zone).toInstant(), d.plusDays(1).atStartOfDay(zone).toInstant());
            daily.add(new DayPoint(d, ds.tableValue() + ds.itemsValue()));
            d = d.plusDays(1);
        }
        return new Summary(st, income, profit, diffs, invDiff, voided, shortSessions, pendingTotal, pending, daily);
    }
}
