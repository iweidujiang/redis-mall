package io.github.iweidujiang.redismall.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class LuaScriptConfig {

    @Bean("unlockScript")
    public DefaultRedisScript<Long> unlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/unlock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("decrStockScript")
    public DefaultRedisScript<Long> decrStockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/decr_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("rateLimitScript")
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/rate_limit.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
