# Redis Mall

商品中心演练工程：缓存读写、击穿与穿透、库存扣减、接口限流。JDK 21 + Spring Boot 3.5.9。

启动后打开 [http://localhost:8080](http://localhost:8080)，左侧切换场景即可实操。

## 启动

先启动 Redis，再启动本演示项目。

```bash
set JAVA_HOME=D:\Java\jdk-21.0.4
cd /d D:\a-github-project\redis-mall
mvn spring-boot:run
```

## 页面场景

| 菜单 | 对应问题 |
| :--- | :--- |
| 商品与缓存 | Cache-Aside、改价删缓存、TTL |
| 缓存穿透 | 不存在的 ID、布隆 / 空值 |
| 缓存击穿 | 无锁回源 vs `SET NX` 互斥 |
| 缓存雪崩 | 相同 TTL vs 随机 TTL |
| 库存超卖 | GET+SET vs Lua，以及正式下单 |
| 接口限流 | 连发详情，观察 429 |
| 大 Key / 热 Key | 造 Hash、打热点，配合 redis-cli |

目录商品：`1001` 手机壳、`1002` 充电头、`1003` 数据线。商品表在内存里模拟数据库，每次查询计一次 DB 命中。
