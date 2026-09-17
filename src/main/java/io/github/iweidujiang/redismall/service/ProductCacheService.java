package io.github.iweidujiang.redismall.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iweidujiang.redismall.domain.Product;
import io.github.iweidujiang.redismall.redis.RedisKeys;
import io.github.iweidujiang.redismall.store.CatalogInitializer;
import io.github.iweidujiang.redismall.store.ProductStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProductCacheService {

    public static final String NIL = "__nil__";

    private final ProductStore store;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<Long> unlockScript;
    private final CatalogInitializer catalogInitializer;

    @Value("${mall.cache-ttl-seconds}")
    private int cacheTtlSeconds;
    @Value("${mall.cache-ttl-jitter-seconds}")
    private int cacheTtlJitterSeconds;
    @Value("${mall.empty-ttl-seconds}")
    private int emptyTtlSeconds;
    @Value("${mall.lock-seconds}")
    private int lockSeconds;

    public ProductCacheService(ProductStore store,
                               StringRedisTemplate redis,
                               ObjectMapper objectMapper,
                               @Qualifier("unlockScript") DefaultRedisScript<Long> unlockScript,
                               CatalogInitializer catalogInitializer) {
        this.store = store;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.unlockScript = unlockScript;
        this.catalogInitializer = catalogInitializer;
    }

    public Optional<Product> get(long id) {
        String cacheKey = RedisKeys.product(id);
        String cached = redis.opsForValue().get(cacheKey);
        if (NIL.equals(cached)) {
            return Optional.empty();
        }
        if (cached != null) {
            return Optional.of(readJson(cached));
        }
        if (!mightExist(id)) {
            return Optional.empty();
        }
        return loadWithMutex(id, cacheKey);
    }

    /**
     * 错误示范：缓存没有就直接打 DB，也不缓存空值。并发一来，数据库会被打穿。
     */
    public Optional<Product> getNaive(long id) {
        String cached = redis.opsForValue().get(RedisKeys.product(id));
        if (cached != null && !NIL.equals(cached)) {
            return Optional.of(readJson(cached));
        }
        return store.findById(id);
    }

    public Product updatePrice(long id, int priceCents) {
        Product current = store.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在: " + id));
        Product updated = current.withPrice(priceCents);
        store.save(updated);
        redis.delete(RedisKeys.product(id));
        return updated;
    }

    public Duration nextTtl() {
        int jitter = cacheTtlJitterSeconds <= 0 ? 0 : ThreadLocalRandom.current().nextInt(cacheTtlJitterSeconds + 1);
        return Duration.ofSeconds(cacheTtlSeconds + jitter);
    }

    private Optional<Product> loadWithMutex(long id, String cacheKey) {
        String lockKey = RedisKeys.productLock(id);
        String token = UUID.randomUUID().toString();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, token, Duration.ofSeconds(lockSeconds));
        try {
            if (Boolean.TRUE.equals(locked)) {
                String again = redis.opsForValue().get(cacheKey);
                if (NIL.equals(again)) {
                    return Optional.empty();
                }
                if (again != null) {
                    return Optional.of(readJson(again));
                }
                Optional<Product> fromDb = store.findById(id);
                writeCache(cacheKey, fromDb);
                return fromDb;
            }
            Thread.sleep(50);
            String retry = redis.opsForValue().get(cacheKey);
            if (NIL.equals(retry)) {
                return Optional.empty();
            }
            if (retry != null) {
                return Optional.of(readJson(retry));
            }
            return store.findById(id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return store.findById(id);
        } finally {
            if (Boolean.TRUE.equals(locked)) {
                redis.execute(unlockScript, List.of(lockKey), token);
            }
        }
    }

    private void writeCache(String cacheKey, Optional<Product> product) {
        try {
            if (product.isEmpty()) {
                redis.opsForValue().set(cacheKey, NIL, Duration.ofSeconds(emptyTtlSeconds));
                return;
            }
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(product.get()), nextTtl());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化商品失败", e);
        }
    }

    private boolean mightExist(long id) {
        if (!catalogInitializer.bloomReady()) {
            return true;
        }
        Long exists = redis.execute((RedisCallback<Long>) connection -> {
            Object raw = connection.execute(
                    "BF.EXISTS",
                    RedisKeys.PRODUCT_BLOOM.getBytes(StandardCharsets.UTF_8),
                    String.valueOf(id).getBytes(StandardCharsets.UTF_8)
            );
            if (raw instanceof Long l) {
                return l;
            }
            return 0L;
        });
        return exists != null && exists == 1L;
    }

    private Product readJson(String json) {
        try {
            return objectMapper.readValue(json, Product.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("反序列化商品失败", e);
        }
    }
}
