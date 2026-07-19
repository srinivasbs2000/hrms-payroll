package com.acme.hrms.payroll.platform;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {
  @Id @GeneratedValue protected UUID id;
  @Column(nullable = false, updatable = false) protected UUID tenantId;
  @Version @Column(nullable = false) protected long versionNo;
  @CreatedDate @Column(nullable = false, updatable = false) protected Instant createdAt;
  @CreatedBy @Column(nullable = false, updatable = false) protected String createdBy;
  @LastModifiedDate @Column(nullable = false) protected Instant updatedAt;
  @LastModifiedBy @Column(nullable = false) protected String updatedBy;

  @PrePersist
  void assignTenant() {
    tenantId = TenantContext.require();
  }

  public UUID id() {
    return id;
  }
}
