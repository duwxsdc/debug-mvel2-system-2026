package com.etl.engine.config;

import com.etl.engine.engine.MvelSandboxEngine;
import com.etl.engine.engine.SqlExecutionEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class EngineConfig {

    @Bean
    public MvelSandboxEngine mvelSandboxEngine() {
        return new MvelSandboxEngine(5000);
    }

    @Bean
    public SqlExecutionEngine sqlExecutionEngine(JdbcTemplate jdbcTemplate) {
        return new SqlExecutionEngine(jdbcTemplate);
    }
}
