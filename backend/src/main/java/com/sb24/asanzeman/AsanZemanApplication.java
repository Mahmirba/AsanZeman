package com.sb24.asanzeman;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Asan Zeman core: a Spring Modulith modular monolith (ADR-001) whose modules are the
 * direct sub-packages of this package. Each module is a hexagon (ADR-017):
 * {@code domain} (pure Java), {@code application} (use cases), {@code infrastructure} (adapters).
 */
@SpringBootApplication
@Modulithic(systemName = "AsanZeman", sharedModules = "shared")
public class AsanZemanApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsanZemanApplication.class, args);
    }
}
