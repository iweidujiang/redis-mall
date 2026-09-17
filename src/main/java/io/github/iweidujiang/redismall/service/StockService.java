package io.github.iweidujiang.redismall.service;

import io.github.iweidujiang.redismall.redis.RedisKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 库存扣减。正式路径用 Lua；GET 再 SET 只留给超卖对比。下单入口另有一把按商品的锁。
 *
 * @author https://github.com/iweidujiang
 */
@Service
public class StockService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> decrStockScript;
    private final DefaultRedisScript<Long> unlockScript;

    public StockService(StringRedisTemplate redis,
                        @Qualifier("decrStockScript") DefaultRedisScript<Long> decrStockScript,
                        @Qualifier("unlockScript") DefaultRedisScript<Long> unlockScript) {
        this.redis = redis;
        this.decrStockScript = decrStockScript;
        this.unlockScript = unlockScript;
    }

    public long deductLua(long productId, int qty) {
        Long left = redis.execute(decrStockScript, List.of(RedisKeys.stock(productId)), String.valueOf(qty));
        return left == null ? -1 : left;
    }

    /**
     * 错误示范：先 GET 再 SET。两个请求同时看到库存 1，会卖出 2 件。
     */
    public long deductNaive(long productId, int qty) {
        String key = RedisKeys.stock(productId);
        String raw = redis.opsForValue().get(key);
        int stock = raw == null ? 0 : Integer.parseInt(raw);
        if (stock < qty) {
            return -1;
        }
        try {
            Thread.sleep(20);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        int left = stock - qty;
        redis.opsForValue().set(key, String.valueOf(left));
        return left;
    }

    public boolean tryLockOrder(long productId, String token, Duration ttl) {
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent(RedisKeys.orderLock(productId), token, ttl)
        );
    }

    public void unlockOrder(long productId, String token) {
        redis.execute(unlockScript, List.of(RedisKeys.orderLock(productId)), token);
    }

    public String newToken() {
        return UUID.randomUUID().toString();
    }

    public String currentStock(long productId) {
        return redis.opsForValue().get(RedisKeys.stock(productId));
    }

    public void resetStock(long productId, int stock) {
        redis.opsForValue().set(RedisKeys.stock(productId), String.valueOf(stock));
    }
}
