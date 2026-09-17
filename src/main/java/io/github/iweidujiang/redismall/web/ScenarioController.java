package io.github.iweidujiang.redismall.web;

import io.github.iweidujiang.redismall.domain.Product;
import io.github.iweidujiang.redismall.redis.RedisKeys;
import io.github.iweidujiang.redismall.service.ProductCacheService;
import io.github.iweidujiang.redismall.service.StockService;
import io.github.iweidujiang.redismall.store.CatalogInitializer;
import io.github.iweidujiang.redismall.store.ProductStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 专门用来制造问题、对比修法。正式接口不要走这里。
 */
@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {

    private final ProductCacheService productCacheService;
    private final ProductStore productStore;
    private final StockService stockService;
    private final StringRedisTemplate redis;

    private final CatalogInitializer catalogInitializer;

    public ScenarioController(ProductCacheService productCacheService,
                              ProductStore productStore,
                              StockService stockService,
                              StringRedisTemplate redis,
                              CatalogInitializer catalogInitializer) {
        this.productCacheService = productCacheService;
        this.productStore = productStore;
        this.stockService = stockService;
        this.redis = redis;
        this.catalogInitializer = catalogInitializer;
    }

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Product p : productStore.findAll()) {
            Long ttl = redis.getExpire(RedisKeys.product(p.id()));
            items.add(Map.of(
                    "id", p.id(),
                    "name", p.name(),
                    "priceCents", p.priceCents(),
                    "stock", Optional.ofNullable(stockService.currentStock(p.id())).orElse("0"),
                    "ttlSeconds", ttl == null ? -2 : ttl
            ));
        }
        return ApiResult.ok(Map.of(
                "bloomReady", catalogInitializer.bloomReady(),
                "dbHits", productStore.queryHits(),
                "items", items
        ));
    }

    @GetMapping("/db-hits")
    public ApiResult<Map<String, Integer>> dbHits() {
        return ApiResult.ok(Map.of("dbHits", productStore.queryHits()));
    }

    @PostMapping("/reset-hits")
    public ApiResult<Void> resetHits() {
        productStore.resetHits();
        return ApiResult.ok(null);
    }

    @GetMapping("/product-naive/{id}")
    public ApiResult<Product> naiveGet(@PathVariable long id) {
        return productCacheService.getNaive(id)
                .map(ApiResult::ok)
                .orElseGet(() -> ApiResult.fail("商品不存在"));
    }

    @PostMapping("/stampede")
    public ApiResult<Map<String, Object>> stampede(@RequestParam(defaultValue = "1001") long id,
                                                   @RequestParam(defaultValue = "20") int threads,
                                                   @RequestParam(defaultValue = "false") boolean naive) {
        redis.delete(RedisKeys.product(id));
        productStore.resetHits();
        int dbHits = runConcurrent(threads, () -> {
            if (naive) {
                productCacheService.getNaive(id);
            } else {
                productCacheService.get(id);
            }
            return null;
        });
        return ApiResult.ok(Map.of(
                "mode", naive ? "naive" : "mutex",
                "threads", threads,
                "dbHits", dbHits
        ));
    }

    @PostMapping("/avalanche")
    public ApiResult<Map<String, Object>> avalanche(@RequestParam(defaultValue = "false") boolean naive,
                                                    @RequestParam(defaultValue = "20") int keys) {
        int count = Math.min(Math.max(keys, 2), 50);
        List<Long> ttls = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            String key = RedisKeys.avalanche(i);
            Duration ttl = naive ? Duration.ofSeconds(30) : productCacheService.nextTtl();
            redis.opsForValue().set(key, "cached", ttl);
            Long expire = redis.getExpire(key);
            ttls.add(expire == null ? -2L : expire);
        }
        return ApiResult.ok(Map.of(
                "mode", naive ? "same-ttl" : "jitter",
                "keys", count,
                "distinctTtl", ttls.stream().distinct().count(),
                "minTtl", Collections.min(ttls),
                "maxTtl", Collections.max(ttls),
                "ttlSeconds", ttls,
                "hint", naive
                        ? "同一秒过期，回源会挤在一起"
                        : "TTL 散开后，过期不会扎堆"
        ));
    }

    @PostMapping("/stock/reset")
    public ApiResult<String> resetStock(@RequestParam(defaultValue = "1001") long productId,
                                        @RequestParam(defaultValue = "10") int stock) {
        stockService.resetStock(productId, stock);
        return ApiResult.ok(stockService.currentStock(productId));
    }

    @PostMapping("/stock/race")
    public ApiResult<Map<String, Object>> stockRace(@RequestParam(defaultValue = "1001") long productId,
                                                    @RequestParam(defaultValue = "20") int threads,
                                                    @RequestParam(defaultValue = "false") boolean naive) {
        stockService.resetStock(productId, 10);
        int[] sold = {0};
        int[] rejected = {0};
        runConcurrent(threads, () -> {
            long left = naive
                    ? stockService.deductNaive(productId, 1)
                    : stockService.deductLua(productId, 1);
            if (left >= 0) {
                synchronized (sold) {
                    sold[0]++;
                }
            } else {
                synchronized (rejected) {
                    rejected[0]++;
                }
            }
            return null;
        });
        return ApiResult.ok(Map.of(
                "mode", naive ? "naive" : "lua",
                "threads", threads,
                "sold", sold[0],
                "rejected", rejected[0],
                "stockLeft", stockService.currentStock(productId)
        ));
    }

    @PostMapping("/big-key")
    public ApiResult<Map<String, Object>> bigKey(@RequestParam(defaultValue = "20000") int fields) {
        String key = RedisKeys.bigHash();
        redis.delete(key);
        for (int i = 1; i <= fields; i++) {
            redis.opsForHash().put(key, "field_" + i, "value_" + i);
        }
        return ApiResult.ok(Map.of(
                "key", key,
                "fields", fields,
                "hint", "redis-cli: MEMORY USAGE mall:big:hash 然后 UNLINK mall:big:hash"
        ));
    }

    @PostMapping("/hot")
    public ApiResult<Map<String, Object>> hot(@RequestParam(defaultValue = "500") int times) {
        Optional<Product> last = Optional.empty();
        for (int i = 0; i < times; i++) {
            last = productCacheService.get(1001);
        }
        return ApiResult.ok(Map.of(
                "key", RedisKeys.hotProduct(),
                "times", times,
                "product", last.orElse(null),
                "hint", "redis-cli: HOTKEYS START METRICS 2 CPU NET 然后 HOTKEYS GET"
        ));
    }

    private int runConcurrent(int threads, Callable<Void> task) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(task));
            }
            for (Future<Void> f : futures) {
                f.get();
            }
        } catch (Exception e) {
            throw new IllegalStateException("并发执行失败", e);
        } finally {
            pool.shutdownNow();
        }
        return productStore.queryHits();
    }
}
