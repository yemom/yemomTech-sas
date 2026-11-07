package com.bizgrow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the YemomTech (BizGrow) backend.
 *
 * <p>Architecture: <b>modular monolith</b>. One deployable Spring Boot application with
 * package-level module boundaries ({@code auth}, {@code organization}, {@code membership},
 * {@code invitation}, {@code website}, {@code audit}, ...). Modules communicate through
 * their public services — never through each other's repositories — so a module can be
 * extracted into a separate service later without rewriting business logic.</p>
 */
@SpringBootApplication
public class BizGrowApplication {

    public static void main(String[] args) {
        SpringApplication.run(BizGrowApplication.class, args);
    }
}
