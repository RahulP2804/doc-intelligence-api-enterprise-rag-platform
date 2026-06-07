package com.docintellect.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DocIntelligenceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocIntelligenceApplication.class, args);
    }
}
