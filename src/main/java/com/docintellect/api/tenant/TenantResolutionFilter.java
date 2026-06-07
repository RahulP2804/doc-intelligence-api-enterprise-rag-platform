package com.docintellect.api.tenant;

import com.docintellect.api.model.Tenant;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class TenantResolutionFilter implements Filter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/health", "/actuator", "/api/v1/tenants"
    );

    private final TenantRepository tenantRepository;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String tenantId = httpRequest.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\":\"Missing X-Tenant-ID header\",\"code\":\"TENANT_HEADER_MISSING\"}"
            );
            return;
        }

        Optional<Tenant> tenant = tenantRepository.findByTenantIdAndActiveTrue(tenantId.trim());
        if (tenant.isEmpty()) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            httpResponse.setContentType("application/json");
            httpResponse.getWriter().write(
                    "{\"error\":\"Unknown or inactive tenant\",\"code\":\"TENANT_NOT_FOUND\"}"
            );
            return;
        }

        TenantContext.setTenantId(tenantId.trim());
        TenantContext.setSchemaName(tenant.get().getSchemaName());

        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith) || path.startsWith("/metrics");
    }
}
