package com.YemomBizGrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point of the YemomTech (YemomBizGrow) backend.
 *
 * <p>Architecture: <b>modular monolith</b>. One deployable Spring Boot application with
 * package-level module boundaries ({@code auth}, {@code organization}, {@code membership},
 * {@code invitation}, {@code website}, {@code audit}, ...). Modules communicate through
 * their public services — never through each other's repositories — so a module can be
 * extracted into a separate service later without rewriting business logic.</p>
 */
@SpringBootApplication
@EnableAsync
public class YemomBizGrowApplication {

    public static void main(String[] args) {
        SpringApplication.run(YemomBizGrowApplication.class, args);
    }
}
