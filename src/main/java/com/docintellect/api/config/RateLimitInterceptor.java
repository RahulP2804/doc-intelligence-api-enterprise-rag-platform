package com.docintellect.api.config;

import com.docintellect.api.config.AppProperties;
import com.docintellect.api.model.Tenant;
import com.docintellect.api.tenant.TenantContext;
import com.docintellect.api.tenant.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_PREFIX = "ratelimit:";

    private final RedisTemplate<String, String> redisTemplate;
    private final AppProperties appProperties;
    private final TenantRepository tenantRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        String tenantId = TenantContext.getTenantId();
        if (tenantId == null) return true;

        Tenant tenant = tenantRepository.findByTenantIdAndActiveTrue(tenantId).orElse(null);
        if (tenant == null) return true;

        int limit = resolveLimit(tenant.getTier());
        int windowSeconds = appProperties.getRateLimit().getWindowSeconds();
        String key = RATE_LIMIT_PREFIX + tenantId;

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        if (count != null && count > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Rate limit exceeded\",\"code\":\"RATE_LIMIT_EXCEEDED\",\"tenantId\":\"" + tenantId + "\"}"
            );
            log.warn("Rate limit exceeded for tenant {} (count={}, limit={})", tenantId, count, limit);
            return false;
        }

        return true;
    }

    private int resolveLimit(Tenant.Tier tier) {
        return appProperties.getRateLimit().getTiers().getOrDefault(tier.name(), 20);
    }
}
