package io.github.iweidujiang.redismall;

import io.github.iweidujiang.redismall.store.CatalogInitializer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RedisMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(RedisMallApplication.class, args);
    }

    @Bean
    ApplicationRunner loadCatalog(CatalogInitializer loader) {
        return args -> loader.load();
    }
}
