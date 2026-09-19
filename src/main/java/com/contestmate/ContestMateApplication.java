package com.contestmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContestMateApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContestMateApplication.class, args);
    }
}

