package com.billar.service;

import com.billar.dto.Dtos.*;
import com.billar.error.ApiException;
import com.billar.model.*;
import com.billar.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Mesas, consumos, cobros, ventas rápidas, anulaciones y fiados. */
@Service
public class PosService {
    private final TableRepo tables;
    private final SessionRepo sessions;
    private final ProductRepo products;
    private final SaleRepo sales;
    private final SettingsService settings;
    private final BillingService billing;
    private final AuditService audit;

    public PosService(TableRepo tables, SessionRepo sessions, ProductRepo products, SaleRepo sales,
                      SettingsService settings, BillingService billing, AuditService audit) {
        this.tables = tables;
        this.sessions = sessions;
        this.products = products;
        this.sales = sales;
        this.settings = settings;
        this.billing = billing;
        this.audit = audit;
    }

    // ---------- sesiones de mesa ----------
    @Transactional(readOnly = true)
    public List<SessionView> openSessions() {
        Instant now = Instant.now();
        return sessions.findByStatus(SessionStatus.OPEN).stream().map(s -> view(s, now)).toList();
    }

    @Transactional
    public SessionView start(Long tableId, String user) {
        PoolTable t = tables.findById(tableId).filter(PoolTable::isActive)
                .orElseThrow(() -> ApiException.notFound("La mesa no existe"));
        if (sessions.existsByTableIdAndStatus(tableId, SessionStatus.OPEN)) {
            throw ApiException.conflict("La mesa ya está en uso");
        }
        TableSession s = new TableSession();
        s.setTable(t);
        s.setStartedAt(Instant.now());   // la hora de inicio la pone el servidor, el empleado no puede editarla
        s.setOpenedBy(user);
        s.setStatus(SessionStatus.OPEN);
        sessions.save(s);
        audit.log(user, "Inició " + t.getName());
        return view(s, Instant.now());
    }

    @Transactional
    public SessionView addItem(Long sessionId, Long productId, Long playerId, String user) {
        TableSession s = open(sessionId);
        Product p = products.findById(productId).filter(Product::isActive)
                .orElseThrow(() -> ApiException.notFound("El producto no existe"));
        if (p.getStock() <= 0) throw ApiException.conflict("Sin stock de " + p.getName());
        p.setStock(p.getStock() - 1);
        Long player = playerOf(s, playerId);
        SessionItem item = s.getItems().stream()
                .filter(i -> i.getProductId().equals(productId) && Objects.equals(i.getPlayerId(), player)).findFirst().orElse(null);
        if (item == null) {
            item = new SessionItem();
            item.setSession(s);
            item.setProductId(productId);
            item.setPlayerId(player);
            item.setName(p.getName());
            item.setPrice(p.getPrice());
            item.setCost(p.getCost());
            item.setQty(0);
            s.getItems().add(item);
        }
        item.setQty(item.getQty() + 1);
        return view(s, Instant.now());
    }

    @Transactional
    public SessionView removeItem(Long sessionId, Long productId, Long playerId, String user) {
        TableSession s = open(sessionId);
        SessionItem item = s.getItems().stream()
                .filter(i -> i.getProductId().equals(productId) && Objects.equals(i.getPlayerId(), s.getPlayers().isEmpty() ? null : playerId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Ese producto no está en la cuenta"));
        products.findById(productId).ifPresent(p -> p.setStock(p.getStock() + 1));
        item.setQty(item.getQty() - 1);
        if (item.getQty() <= 0) s.getItems().remove(item);
        // quitar un consumo queda registrado: es una de las maneras clásicas de ocultar ventas
        audit.log(user, "Quitó 1 " + item.getName() + " de " + s.getName());
        return view(s, Instant.now());
    }

    @Transactional
    public SaleView charge(Long sessionId, ChargeReq req, String user) {
        TableSession s = open(sessionId);
        validate(req.method(), req.customerName(), req.note());
        Instant end = Instant.now();
        Duration d = Duration.between(s.getStartedAt(), end);
        int step = settings.get().getBillingStepMinutes();

        Sale sale = new Sale();
        sale.setType(s.getTable() == null ? SaleType.QUICK : SaleType.TABLE);
        sale.setTableName(s.getName());
        sale.setStartedAt(s.getStartedAt());
        sale.setEndedAt(end);
        sale.setActualSeconds(d.toSeconds());
        sale.setBilledMinutes(billing.billedMinutes(d, step));
        sale.setTimeValue(billing.timeValue(d, s.getRate(), step));
        for (SessionItem i : s.getItems()) {
            SaleItem si = new SaleItem();
            si.setSale(sale);
            si.setProductId(i.getProductId());
            si.setName(i.getName());
            si.setPrice(i.getPrice());
            si.setCost(i.getCost());
            si.setQty(i.getQty());
            sale.getItems().add(si);
        }
        finish(sale, req.method(), req.customerName(), req.note(), user);
        s.setStatus(SessionStatus.CLOSED);
        s.setEndedAt(end);
        audit.log(user, "Cobró " + s.getName() + " por " + sale.getTotal() + " (" + req.method() + ")"
                + (blank(req.note()) ? "" : ", motivo: " + req.note()));
        return SaleView.of(sale);
    }

    // ---------- venta rápida (sin mesa) ----------
    @Transactional
    public SaleView quickSale(QuickSaleReq req, String user) {
        validate(req.method(), req.customerName(), req.note());
        Sale sale = new Sale();
        sale.setType(SaleType.QUICK);
        sale.setTableName("Venta rápida");
        Instant now = Instant.now();
        sale.setStartedAt(now);
        sale.setEndedAt(now);
        for (QuickItem q : req.items()) {
            Product p = products.findById(q.productId()).filter(Product::isActive)
                    .orElseThrow(() -> ApiException.notFound("El producto no existe"));
            if (p.getStock() < q.qty()) throw ApiException.conflict("Sin stock suficiente de " + p.getName());
            p.setStock(p.getStock() - q.qty());
            SaleItem si = new SaleItem();
            si.setSale(sale);
            si.setProductId(p.getId());
            si.setName(p.getName());
            si.setPrice(p.getPrice());
            si.setCost(p.getCost());
            si.setQty(q.qty());
            sale.getItems().add(si);
        }
        finish(sale, req.method(), req.customerName(), req.note(), user);
        audit.log(user, "Venta rápida por " + sale.getTotal() + " (" + req.method() + ")");
        return SaleView.of(sale);
    }

    // ---------- ventas ----------
    @Transactional(readOnly = true)
    public List<SaleView> salesBetween(Instant from, Instant to) {
        return sales.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to).stream().map(SaleView::of).toList();
    }

    @Transactional
    public SaleView voidSale(Long id, String user) {
        Sale s = sale(id);
        if (s.getStatus() == SaleStatus.VOIDED) throw ApiException.conflict("La venta ya estaba anulada");
        for (SaleItem i : s.getItems()) {
            products.findById(i.getProductId()).ifPresent(p -> p.setStock(p.getStock() + i.getQty()));
        }
        s.setStatus(SaleStatus.VOIDED);
        s.setVoidedBy(user);
        s.setVoidedAt(Instant.now());
        audit.log(user, "Anuló venta #" + id + " (" + s.getTableName() + ") por " + s.getTotal());
        return SaleView.of(s);
    }

    @Transactional
    public SaleView payCredit(Long id, PayMethod method, String user) {
        Sale s = sale(id);
        if (s.getMethod() != PayMethod.CREDIT || s.isPaid() || s.getStatus() == SaleStatus.VOIDED) {
            throw ApiException.conflict("Esa venta no tiene un fiado pendiente");
        }
        if (method != PayMethod.CASH && method != PayMethod.TRANSFER) throw ApiException.badRequest("Paga en efectivo o transferencia");
        s.setPaid(true);
        s.setPaidAt(Instant.now());
        s.setPaidMethod(method);
        audit.log(user, "Cobró fiado de " + s.getCustomerName() + " por " + s.getTotal() + " (" + method + ")");
        return SaleView.of(s);
    }

    // ---------- helpers ----------
    private void finish(Sale sale, PayMethod method, String customer, String note, String user) {
        long itemsTotal = 0, itemsCost = 0;
        for (SaleItem i : sale.getItems()) {
            itemsTotal += i.getPrice() * i.getQty();
            itemsCost += i.getCost() * i.getQty();
        }
        sale.setItemsTotal(itemsTotal);
        sale.setItemsCost(itemsCost);
        sale.setTotal(sale.getTimeValue() + itemsTotal);
        sale.setMethod(method);
        sale.setCustomerName(customer);
        sale.setNote(note);
        sale.setCreatedBy(user);
        sale.setCreatedAt(Instant.now());
        sale.setStatus(SaleStatus.ACTIVE);
        sale.setPaid(method != PayMethod.CREDIT);
        sales.save(sale);
    }

    private void validate(PayMethod m, String customer, String note) {
        if (m == PayMethod.CREDIT && blank(customer)) throw ApiException.badRequest("El fiado necesita el nombre del cliente");
        if (m == PayMethod.COURTESY && blank(note)) throw ApiException.badRequest("La cortesía necesita un motivo");
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private TableSession open(Long id) {
        TableSession s = sessions.findById(id).orElseThrow(() -> ApiException.notFound("La sesión no existe"));
        if (s.getStatus() != SessionStatus.OPEN) throw ApiException.conflict("La mesa ya fue cobrada");
        return s;
    }

    private Sale sale(Long id) {
        return sales.findById(id).orElseThrow(() -> ApiException.notFound("La venta no existe"));
    }

    @Transactional
    public SessionView openTab(String label, String user) {
        TableSession s = new TableSession();
        s.setLabel(label.trim());
        s.setStartedAt(Instant.now());
        s.setOpenedBy(user);
        sessions.save(s);
        audit.log(user, "Abrió cuenta: " + s.getLabel());
        return view(s, Instant.now());
    }

    // ---------- jugadores y series ----------
    @Transactional
    public SessionView addPlayer(Long sessionId, String name, String user) {
        TableSession s = open(sessionId);
        String n = name.trim();
        if (s.getPlayers().stream().anyMatch(p -> p.getName().equalsIgnoreCase(n))) {
            throw ApiException.conflict("Ya hay un jugador con ese nombre");
        }
        SessionPlayer p = new SessionPlayer();
        p.setSession(s);
        p.setName(n);
        s.getPlayers().add(p);
        sessions.flush();   // para que el jugador ya tenga su id al devolver la vista
        audit.log(user, "Agregó al jugador " + n + " en " + s.getName());
        return view(s, Instant.now());
    }

    @Transactional
    public SessionView startSeries(Long sessionId, String user) {
        TableSession s = open(sessionId);
        if (s.getTable() == null) throw ApiException.badRequest("Las series son solo para mesas de billar");
        if (s.getPlayers().size() < 2) throw ApiException.badRequest("Agrega al menos dos jugadores");
        if (runningSeries(s) != null) throw ApiException.conflict("Ya hay una serie en juego");
        SessionSeries x = new SessionSeries();
        x.setSession(s);
        x.setStartedAt(Instant.now());
        s.getSeries().add(x);
        audit.log(user, "Inició una serie en " + s.getName());
        return view(s, Instant.now());
    }

    /** Cierra la serie en juego: el tiempo se detiene y se le cobra al jugador que perdió. */
    @Transactional
    public SessionView endSeries(Long sessionId, Long loserId, String user) {
        TableSession s = open(sessionId);
        SessionSeries x = runningSeries(s);
        if (x == null) throw ApiException.conflict("No hay una serie en juego");
        SessionPlayer loser = s.getPlayers().stream().filter(p -> Objects.equals(p.getId(), loserId)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("Ese jugador no está en la mesa"));
        Instant end = Instant.now();
        Duration d = Duration.between(x.getStartedAt(), end);
        int step = settings.get().getBillingStepMinutes();
        x.setEndedAt(end);
        x.setLoserId(loserId);
        x.setBilledMinutes(billing.billedMinutes(d, step));
        x.setTimeValue(billing.timeValue(d, s.getRate(), step));
        audit.log(user, "Serie terminada en " + s.getName() + ": perdió " + loser.getName()
                + ", " + x.getBilledMinutes() + " min por " + x.getTimeValue());
        return view(s, end);
    }

    /** Mueve unidades de un consumo entre General (sin jugador) y los jugadores; la cuenta sigue abierta hasta el cobro. */
    @Transactional
    public SessionView moveItem(Long sessionId, MoveReq req, String user) {
        TableSession s = open(sessionId);
        playerOf(s, req.fromPlayerId());
        playerOf(s, req.toPlayerId());
        if (Objects.equals(req.fromPlayerId(), req.toPlayerId())) throw ApiException.badRequest("El origen y el destino son el mismo");
        SessionItem from = s.getItems().stream()
                .filter(i -> i.getProductId().equals(req.productId()) && Objects.equals(i.getPlayerId(), req.fromPlayerId())).findFirst()
                .orElseThrow(() -> ApiException.notFound("Ese consumo no está en la cuenta"));
        if (req.qty() > from.getQty()) throw ApiException.badRequest("No hay tantas unidades para mover");
        SessionItem to = s.getItems().stream()
                .filter(i -> i.getProductId().equals(req.productId()) && Objects.equals(i.getPlayerId(), req.toPlayerId())).findFirst().orElse(null);
        if (to == null) {
            to = new SessionItem();
            to.setSession(s);
            to.setProductId(from.getProductId());
            to.setName(from.getName());
            to.setPrice(from.getPrice());
            to.setCost(from.getCost());
            to.setPlayerId(req.toPlayerId());
            to.setQty(0);
            s.getItems().add(to);
        }
        to.setQty(to.getQty() + req.qty());
        from.setQty(from.getQty() - req.qty());
        if (from.getQty() == 0) s.getItems().remove(from);
        audit.log(user, "Movió " + req.qty() + " x " + from.getName() + " en " + s.getName());
        return view(s, Instant.now());
    }

    /** Una venta por jugador: sus series perdidas + sus consumos. Los consumos generales (sin jugador) los paga quien perdió (sharedTo). */
    @Transactional
    public List<SaleView> chargePlayers(Long sessionId, PlayersChargeReq req, String user) {
        TableSession s = open(sessionId);
        if (runningSeries(s) != null) throw ApiException.conflict("Hay una serie en juego: termínala antes de cobrar");
        boolean generales = s.getItems().stream().anyMatch(i -> i.getPlayerId() == null);
        if (generales && s.getPlayers().stream().noneMatch(p -> Objects.equals(p.getId(), req.sharedTo()))) {
            throw ApiException.badRequest("Elige quién perdió: él paga los consumos generales");
        }
        Instant end = Instant.now();
        List<Sale> result = new ArrayList<>();
        for (SessionPlayer p : s.getPlayers()) {
            PlayerView pv = playerView(s, p, req.sharedTo());
            if (pv.total() == 0) continue;
            PlayerPay pay = req.payments().stream().filter(x -> Objects.equals(x.playerId(), p.getId())).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Falta el pago de " + p.getName()));
            validate(pay.method(), p.getName(), pay.note());
            List<SessionSeries> lost = s.getSeries().stream()
                    .filter(x -> x.getEndedAt() != null && Objects.equals(x.getLoserId(), p.getId())).toList();
            Sale sale = new Sale();
            sale.setType(SaleType.TABLE);
            sale.setTableName(s.getName() + " - " + p.getName());
            sale.setStartedAt(s.getStartedAt());
            sale.setEndedAt(end);
            sale.setActualSeconds(lost.stream().mapToLong(x -> Duration.between(x.getStartedAt(), x.getEndedAt()).toSeconds()).sum());
            sale.setBilledMinutes(lost.stream().mapToInt(SessionSeries::getBilledMinutes).sum());
            sale.setTimeValue(pv.timeValue());
            for (SessionItem i : s.getItems()) {
                if (Objects.equals(i.getPlayerId(), p.getId()) || (i.getPlayerId() == null && Objects.equals(req.sharedTo(), p.getId()))) {
                    sale.getItems().add(toSaleItem(sale, i));
                }
            }
            finish(sale, pay.method(), p.getName(), pay.note(), user);
            result.add(sale);
        }
        s.setStatus(SessionStatus.CLOSED);
        s.setEndedAt(end);
        audit.log(user, "Cobró " + s.getName() + " por jugador: " + result.size() + " ventas, total "
                + result.stream().mapToLong(Sale::getTotal).sum());
        return result.stream().map(SaleView::of).toList();
    }

    private SaleItem toSaleItem(Sale sale, SessionItem i) {
        SaleItem si = new SaleItem();
        si.setSale(sale);
        si.setProductId(i.getProductId());
        si.setName(i.getName());
        si.setPrice(i.getPrice());
        si.setCost(i.getCost());
        si.setQty(i.getQty());
        return si;
    }

    private SessionSeries runningSeries(TableSession s) {
        return s.getSeries().stream().filter(x -> x.getEndedAt() == null).findFirst().orElse(null);
    }

    /** El consumo va a un jugador de la mesa o queda general (sin jugador). */
    private Long playerOf(TableSession s, Long playerId) {
        if (playerId != null && s.getPlayers().stream().noneMatch(p -> Objects.equals(p.getId(), playerId))) {
            throw ApiException.badRequest("Ese jugador no está en la mesa");
        }
        return playerId;
    }

    /** Lo que debe un jugador: tiempo de las series que perdió + sus consumos (+ los generales, si él los paga). */
    private PlayerView playerView(TableSession s, SessionPlayer p, Long sharedTo) {
        List<SessionSeries> lost = s.getSeries().stream()
                .filter(x -> x.getEndedAt() != null && Objects.equals(x.getLoserId(), p.getId())).toList();
        long time = lost.stream().mapToLong(SessionSeries::getTimeValue).sum();
        long items = s.getItems().stream()
                .filter(i -> Objects.equals(i.getPlayerId(), p.getId()) || (i.getPlayerId() == null && Objects.equals(sharedTo, p.getId())))
                .mapToLong(i -> i.getPrice() * i.getQty()).sum();
        return new PlayerView(p.getId(), p.getName(), lost.size(), time, items, time + items);
    }

    private SessionView view(TableSession s, Instant now) {
        int step = settings.get().getBillingStepMinutes();
        List<ItemView> items = s.getItems().stream()
                .map(i -> new ItemView(i.getProductId(), i.getName(), i.getPrice(), i.getQty(), i.getPlayerId())).toList();
        long itemsTotal = items.stream().mapToLong(i -> i.price() * i.qty()).sum();
        SessionSeries running = runningSeries(s);
        // con jugadores solo se cobran las series; sin jugadores, todo el tiempo de la mesa
        long tv = s.getPlayers().isEmpty()
                ? billing.timeValue(Duration.between(s.getStartedAt(), now), s.getRate(), step)
                : s.getSeries().stream().mapToLong(x -> x.getEndedAt() == null
                        ? billing.timeValue(Duration.between(x.getStartedAt(), now), s.getRate(), step) : x.getTimeValue()).sum();
        List<PlayerView> players = s.getPlayers().stream().map(p -> playerView(s, p, null)).toList();
        return new SessionView(s.getId(), s.tableIdOrNull(), s.getName(), s.getRate(), s.getStartedAt(), now,
                items, itemsTotal, tv, players, running == null ? null : running.getStartedAt());
    }
}
