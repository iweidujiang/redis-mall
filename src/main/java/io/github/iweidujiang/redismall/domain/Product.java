package io.github.iweidujiang.redismall.domain;

public record Product(long id, String name, int priceCents, int stock) {

    public Product withPrice(int newPriceCents) {
        return new Product(id, name, newPriceCents, stock);
    }

    public Product withStock(int newStock) {
        return new Product(id, name, priceCents, newStock);
    }
}
