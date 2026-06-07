package com.docintellect.api.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wraps JdbcTemplate to automatically prepend SET search_path to the active tenant schema
 * before every query. Ensures all SQL executes within the correct tenant isolation boundary.
 */
@Component
@RequiredArgsConstructor
public class TenantAwareJdbcTemplate {

    private final JdbcTemplate jdbcTemplate;

    public void execute(String sql, Object... args) {
        setSearchPath();
        jdbcTemplate.update(sql, args);
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        setSearchPath();
        return jdbcTemplate.query(sql, mapper, args);
    }

    public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... args) {
        setSearchPath();
        return jdbcTemplate.queryForObject(sql, mapper, args);
    }

    public int update(String sql, Object... args) {
        setSearchPath();
        return jdbcTemplate.update(sql, args);
    }

    public JdbcTemplate raw() {
        return jdbcTemplate;
    }

    private void setSearchPath() {
        String schema = TenantContext.getSchemaName();
        if (schema == null || schema.isBlank()) {
            throw new IllegalStateException("No tenant schema in context — ensure TenantResolutionFilter ran");
        }
        jdbcTemplate.execute("SET search_path TO " + schema + ", public");
    }
}
