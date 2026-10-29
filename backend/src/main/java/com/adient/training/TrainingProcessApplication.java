package com.adient.training;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TrainingProcessApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrainingProcessApplication.class, args);
    }
}
