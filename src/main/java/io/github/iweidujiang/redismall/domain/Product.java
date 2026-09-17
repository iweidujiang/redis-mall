package io.github.iweidujiang.redismall.domain;

/**
 * 商品。价格用分，避免浮点；stock 只表示目录里的标称库存，真正扣减走 Redis。
 *
 * @param id         商品 ID
 * @param name       名称
 * @param priceCents 售价，单位分
 * @param stock      目录库存，启动时写入 Redis
 * @author https://github.com/iweidujiang
 */
public record Product(long id, String name, int priceCents, int stock) {

    public Product withPrice(int newPriceCents) {
        return new Product(id, name, newPriceCents, stock);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, priceCents, newStock);
    }
}
