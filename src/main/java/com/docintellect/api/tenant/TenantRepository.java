package com.docintellect.api.tenant;

import com.docintellect.api.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findByTenantIdAndActiveTrue(String tenantId);

    boolean existsByTenantId(String tenantId);
}
