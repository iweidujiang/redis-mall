package io.github.iweidujiang.redismall.web;

import io.github.iweidujiang.redismall.domain.Product;
import io.github.iweidujiang.redismall.redis.RedisKeys;
import io.github.iweidujiang.redismall.service.ProductCacheService;
import io.github.iweidujiang.redismall.store.ProductStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 正式商品接口：列表、详情、改价、缓存元数据。走限流拦截器。
 *
 * @author https://github.com/iweidujiang
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductCacheService productCacheService;
    private final ProductStore productStore;
    private final StringRedisTemplate redis;

    public ProductController(ProductCacheService productCacheService,
                             ProductStore productStore,
                             StringRedisTemplate redis) {
        this.productCacheService = productCacheService;
        this.productStore = productStore;
        this.redis = redis;
    }

    @GetMapping
    public ApiResult<List<Product>> list() {
        return ApiResult.ok(new ArrayList<>(productStore.findAll()));
    }

    @GetMapping("/{id}")
    public ApiResult<Product> get(@PathVariable long id) {
        return productCacheService.get(id)
                .map(ApiResult::ok)
                .orElseGet(() -> ApiResult.fail("商品不存在"));
    }

    @PutMapping("/{id}/price")
    public ApiResult<Product> updatePrice(@PathVariable long id, @RequestParam int priceCents) {
        Product updated = productCacheService.updatePrice(id, priceCents);
        return ApiResult.ok(updated);
    }

    @GetMapping("/{id}/cache-meta")
    public ApiResult<Map<String, Object>> cacheMeta(@PathVariable long id) {
        String key = RedisKeys.product(id);
        Long ttl = redis.getExpire(key);
        return ApiResult.ok(Map.of(
                "key", key,
                "ttlSeconds", ttl == null ? -2 : ttl,
                "dbHits", productStore.queryHits()
        ));
    }
}
