package com.omplatform.examples.seata.service;

import io.seata.spring.annotation.GlobalTransactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExampleServiceSeata {

    private final JdbcTemplate jdbcTemplate;

    public ExampleServiceSeata(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GlobalTransactional(name = "examples-create", rollbackFor = Exception.class)
    public void createRecord(String name) {
        jdbcTemplate.update("INSERT INTO example_table(name) VALUES(?)", name);
    }
}

