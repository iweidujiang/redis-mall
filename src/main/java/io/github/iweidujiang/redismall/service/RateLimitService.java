package io.github.iweidujiang.redismall.service;

import io.github.iweidujiang.redismall.redis.RedisKeys;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 按 IP 做分钟级限流。INCR 和 EXPIRE 必须在同一段 Lua 里，拆开会留下永不过期的计数 Key。
 *
 * @author https://github.com/iweidujiang
 */
@Service
public class RateLimitService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> rateLimitScript;

    @Value("${mall.rate-limit-per-minute}")
    private int limitPerMinute;

    public RateLimitService(StringRedisTemplate redis,
                            @Qualifier("rateLimitScript") DefaultRedisScript<Long> rateLimitScript) {
        this.redis = redis;
        this.rateLimitScript = rateLimitScript;
    }

    public boolean allow(String ip) {
        Long ok = redis.execute(rateLimitScript, List.of(RedisKeys.rateLimit(ip)),
                String.valueOf(limitPerMinute), "60");
        return ok != null && ok == 1L;
    }
}
