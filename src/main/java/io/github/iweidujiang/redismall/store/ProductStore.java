package io.github.iweidujiang.redismall.store;

import io.github.iweidujiang.redismall.domain.Product;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 内存商品表，用来模拟数据库。每次按 ID 查询会睡 30ms 并计数，方便对比击穿时打了几次库。
 *
 * @author https://github.com/iweidujiang
 */
@Component
public class ProductStore {

    private final ConcurrentHashMap<Long, Product> table = new ConcurrentHashMap<>();
    private final AtomicInteger queryHits = new AtomicInteger();

    public void save(Product product) {
        table.put(product.id(), product);
    }

    public Optional<Product> findById(long id) {
        queryHits.incrementAndGet();
        simulateDbLatency();
        return Optional.ofNullable(table.get(id));
    }

    public Collection<Product> findAll() {
        return table.values();
    }

    public int queryHits() {
        return queryHits.get();
    }

    public void resetHits() {
        queryHits.set(0);
    }

    private void simulateDbLatency() {
        try {
            Thread.sleep(30);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
