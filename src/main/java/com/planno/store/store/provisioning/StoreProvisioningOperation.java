package com.planno.store.store.provisioning;

import com.planno.store.core.auditing.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "store_provisioning_operations")
public class StoreProvisioningOperation extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false)
    private UUID storeId;

    @Column(nullable = false, length = 30)
    private String status;

    @Column(nullable = false, length = 255)
    private String storeAdminEmail;

    @Column(nullable = false, length = 150)
    private String storeAdminName;

    @Column(nullable = false, length = 32)
    private String planCode;

    protected StoreProvisioningOperation() {
    }

    public StoreProvisioningOperation(String idempotencyKey, String requestHash, UUID storeId,
            String storeAdminEmail, String storeAdminName, String planCode) {
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.storeId = storeId;
        this.storeAdminEmail = storeAdminEmail.trim().toLowerCase();
        this.storeAdminName = storeAdminName.trim();
        this.planCode = planCode;
        this.status = "READY_FOR_CUSTOMIZATION";
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getStoreId() { return storeId; }
    public String getStatus() { return status; }
    public String getStoreAdminEmail() { return storeAdminEmail; }
    public String getStoreAdminName() { return storeAdminName; }
    public String getPlanCode() { return planCode; }
}
