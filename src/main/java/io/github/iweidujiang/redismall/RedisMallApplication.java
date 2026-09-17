package io.github.iweidujiang.redismall;

import io.github.iweidujiang.redismall.store.CatalogInitializer;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Redis Mall 启动入口。启动后灌入商品目录和库存。
 *
 * @author https://github.com/iweidujiang
 */
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
