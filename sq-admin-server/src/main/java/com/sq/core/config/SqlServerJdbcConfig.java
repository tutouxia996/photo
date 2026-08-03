package com.sq.core.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * SQL Server 专用 JdbcTemplate 配置
 * <p>
 * 创建独立的 JdbcTemplate，直接绑定 SQL Server DataSource，
 * 完全绕过 DynamicDataSource 路由，专用于只读数据验证查询。
 * </p>
 * <p>
 * 使用方式：在 Service 中通过 {@code @Qualifier("sqlServerJdbcTemplate")} 注入。
 * </p>
 *
 * @author sq
 */
@Configuration
@ConditionalOnProperty(prefix = "spring.datasource.druid.sqlserver", name = "enabled", havingValue = "true")
public class SqlServerJdbcConfig {

    /**
     * SQL Server 专用 JdbcTemplate
     *
     * @param dataSource SQL Server 数据源（由 DruidConfig.sqlServerDataSource 创建）
     * @return JdbcTemplate 实例
     */
    @Bean(name = "sqlServerJdbcTemplate")
    public JdbcTemplate sqlServerJdbcTemplate(
            @Qualifier("sqlServerDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
