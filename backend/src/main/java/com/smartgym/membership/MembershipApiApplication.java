package com.smartgym.membership;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MembershipApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MembershipApiApplication.class, args);
    }
}
