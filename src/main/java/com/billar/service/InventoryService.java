package com.billar.service;

import com.billar.dto.Dtos.*;
import com.billar.error.ApiException;
import com.billar.model.InventoryCount;
import com.billar.model.Product;
import com.billar.repo.CountRepo;
import com.billar.repo.ProductRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class InventoryService {
    private final ProductRepo products;
    private final CountRepo counts;
    private final AuditService audit;

    public InventoryService(ProductRepo products, CountRepo counts, AuditService audit) {
        this.products = products;
        this.counts = counts;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProductView> list(boolean owner) {
        return products.findByActiveTrueOrderByNameAsc().stream().map(p -> view(p, owner)).toList();
    }

    @Transactional
    public ProductView create(ProductReq r, String user) {
        Product p = new Product();
        p.setName(r.name());
        p.setPrice(r.price());
        p.setCost(r.cost());
        p.setStock(r.initialStock());
        products.save(p);
        audit.log(user, "Creó producto " + r.name());
        return view(p, true);
    }

    @Transactional
    public ProductView update(Long id, ProductReq r, String user) {
        Product p = find(id);
        p.setName(r.name());
        p.setPrice(r.price());
        p.setCost(r.cost());
        audit.log(user, "Editó producto " + r.name() + " (precio " + r.price() + ", costo " + r.cost() + ")");
        return view(p, true);
    }

    @Transactional
    public void delete(Long id, String user) {
        Product p = find(id);
        p.setActive(false);   // no se borra: las ventas anteriores conservan su historial
        audit.log(user, "Eliminó producto " + p.getName());
    }

    @Transactional
    public ProductView purchase(Long id, int qty, String user) {
        Product p = find(id);
        p.setStock(p.getStock() + qty);
        audit.log(user, "Compra: " + qty + " x " + p.getName());
        return view(p, true);
    }

    /** Conteo físico. Registra la diferencia contra el sistema y deja el stock en lo contado. */
    @Transactional
    public int count(CountReq req, String user) {
        int withDiff = 0;
        for (CountLine l : req.lines()) {
            Product p = find(l.productId());
            InventoryCount c = new InventoryCount();
            c.setCountedAt(Instant.now());
            c.setCountedBy(user);
            c.setProductId(p.getId());
            c.setProductName(p.getName());
            c.setSystemStock(p.getStock());
            c.setCountedStock(l.counted());
            c.setDifference(l.counted() - p.getStock());
            c.setUnitCost(p.getCost());
            counts.save(c);
            if (c.getDifference() != 0) {
                withDiff++;
                audit.log(user, "Conteo de " + p.getName() + ": sistema " + c.getSystemStock() + ", contado " + l.counted());
            }
            p.setStock(l.counted());
        }
        return withDiff;
    }

    private Product find(Long id) {
        return products.findById(id).filter(Product::isActive).orElseThrow(() -> ApiException.notFound("El producto no existe"));
    }

    private ProductView view(Product p, boolean owner) {
        return new ProductView(p.getId(), p.getName(), p.getPrice(), owner ? p.getCost() : null, p.getStock());
    }
}
