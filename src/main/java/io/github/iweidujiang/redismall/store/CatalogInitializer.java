package io.github.iweidujiang.redismall.store;

import io.github.iweidujiang.redismall.domain.Product;
import io.github.iweidujiang.redismall.redis.RedisKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 启动时写入三个商品、对应库存，并尝试创建布隆过滤器。镜像没有 Bloom 时会退回空值缓存。
 *
 * @author https://github.com/iweidujiang
 */
@Component
public class CatalogInitializer {

    private static final Logger log = LoggerFactory.getLogger(CatalogInitializer.class);

    private final ProductStore store;
    private final StringRedisTemplate redis;

    public CatalogInitializer(ProductStore store, StringRedisTemplate redis) {
        this.store = store;
        this.redis = redis;
    }

    public void load() {
        List<Product> products = List.of(
                new Product(1001, "手机壳", 2900, 100),
                new Product(1002, "充电头", 5900, 80),
                new Product(1003, "数据线", 1900, 200)
        );
        products.forEach(p -> {
            store.save(p);
            redis.opsForValue().set(RedisKeys.stock(p.id()), String.valueOf(p.stock()));
        });
        initBloom(products);
        log.info("catalog ready, products={}, bloom={}", products.size(), bloomReady);
    }

    private boolean bloomReady;

    private void initBloom(List<Product> products) {
        try {
            redis.execute((RedisCallback<Object>) connection -> {
                connection.execute("BF.RESERVE",
                        RedisKeys.PRODUCT_BLOOM.getBytes(StandardCharsets.UTF_8),
                        "0.01".getBytes(StandardCharsets.UTF_8),
                        "10000".getBytes(StandardCharsets.UTF_8));
                return null;
            });
        } catch (Exception ignored) {
            // 已经存在，或镜像没有 Bloom 模块
        }
        try {
            for (Product p : products) {
                redis.execute((RedisCallback<Object>) connection -> {
                    connection.execute("BF.ADD",
                            RedisKeys.PRODUCT_BLOOM.getBytes(StandardCharsets.UTF_8),
                            String.valueOf(p.id()).getBytes(StandardCharsets.UTF_8));
                    return null;
                });
            }
            bloomReady = true;
        } catch (Exception e) {
            bloomReady = false;
            log.warn("Bloom 不可用，穿透只靠空值缓存: {}", e.getMessage());
        }
    }

    public boolean bloomReady() {
        return bloomReady;
    }
}
