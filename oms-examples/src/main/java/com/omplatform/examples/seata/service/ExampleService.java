package com.omplatform.examples.seata.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExampleService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /*public ExampleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }*/

    // Use Spring local transaction in the example. To enable Seata global transaction,
    // add Seata to the classpath and replace with @GlobalTransactional.
    @Transactional(rollbackFor = Exception.class)
    public void createRecord(String name) {
        jdbcTemplate.update("INSERT INTO example_table(name) VALUES(?)", name);
    }
}

