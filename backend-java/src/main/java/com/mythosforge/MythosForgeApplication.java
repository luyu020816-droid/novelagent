package com.mythosforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 入口：扫描 {@code com.mythosforge} 下全部组件（Web、JPA、Flyway 等）。
 */
@SpringBootApplication
public class MythosForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MythosForgeApplication.class, args);
    }
}
