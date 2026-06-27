package com.omplatform.examples.seata.controller;

import com.omplatform.examples.seata.service.ExampleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExampleController {

    @Autowired
    private ExampleService exampleService;

    /*public ExampleController(ExampleService exampleService) {
        this.exampleService = exampleService;
    }*/

    @PostMapping("/example/create")
    public ResponseEntity<String> create(@RequestParam String name) {
        exampleService.createRecord(name);
        return ResponseEntity.ok("created");
    }
}

