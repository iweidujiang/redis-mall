package io.github.iweidujiang.redismall.redis;

public final class RedisKeys {

    public static final String PRODUCT_BLOOM = "bf:product_id";

    private RedisKeys() {
    }

    public static String product(long id) {
        return "product:" + id;
    }

    public static String productLock(long id) {
        return "lock:product:" + id;
    }

    public static String stock(long productId) {
        return "stock:" + productId;
    }

    public static String orderLock(long productId) {
        return "lock:order:" + productId;
    }

    public static String rateLimit(String ip) {
        return "rate:ip:" + ip;
    }

    public static String hotProduct() {
        return "product:1001";
    }

    public static String bigHash() {
        return "mall:big:hash";
    }

    public static String avalanche(int index) {
        return "mall:av:" + index;
    }
}
