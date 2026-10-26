package com.smartgym.membership;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.smartgym.membership", "com.smartgym.courses"})
@EntityScan(basePackages = "com.smartgym")
@EnableJpaRepositories(basePackages = "com.smartgym")
public class MembershipApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MembershipApiApplication.class, args);
    }
}
