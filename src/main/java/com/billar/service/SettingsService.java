package com.billar.service;

import com.billar.dto.Dtos.SettingsReq;
import com.billar.dto.Dtos.SettingsView;
import com.billar.model.BusinessSettings;
import com.billar.repo.SettingsRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
    private final SettingsRepo repo;
    private final AuditService audit;

    public SettingsService(SettingsRepo repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public BusinessSettings get() {
        return repo.findById(1L).orElseGet(() -> {
            BusinessSettings s = new BusinessSettings();
            s.setId(1L);
            s.setBusinessName("Mi Billar");
            s.setBillingStepMinutes(1);
            s.setBaseCash(50000);
            return repo.save(s);
        });
    }

    @Transactional(readOnly = true)
    public SettingsView view() {
        BusinessSettings s = get();
        return new SettingsView(s.getBusinessName(), s.getBillingStepMinutes(), s.getBaseCash());
    }

    @Transactional
    public SettingsView update(SettingsReq r, String user) {
        BusinessSettings s = get();
        s.setBusinessName(r.businessName());
        s.setBillingStepMinutes(r.billingStepMinutes());
        s.setBaseCash(r.baseCash());
        repo.save(s);
        audit.log(user, "Cambió ajustes: cobro por " + r.billingStepMinutes() + " min, base de caja " + r.baseCash());
        return view();
    }
}
