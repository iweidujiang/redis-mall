package io.github.iweidujiang.redismall.web;

import io.github.iweidujiang.redismall.service.StockService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record PlaceOrderRequest(long productId, int qty) {
    }

    public record PlaceOrderResponse(long productId, int qty, long stockLeft) {
    }

    private final StockService stockService;

    public OrderController(StockService stockService) {
        this.stockService = stockService;
    }

    @PostMapping
    public ApiResult<PlaceOrderResponse> place(@RequestBody PlaceOrderRequest request) {
        if (request.qty() <= 0) {
            return ApiResult.fail("数量必须大于 0");
        }
        String token = stockService.newToken();
        if (!stockService.tryLockOrder(request.productId(), token, Duration.ofSeconds(5))) {
            return ApiResult.fail("有人正在下单，请重试");
        }
        try {
            long left = stockService.deductLua(request.productId(), request.qty());
            if (left < 0) {
                return ApiResult.fail("库存不足");
            }
            return ApiResult.ok(new PlaceOrderResponse(request.productId(), request.qty(), left));
        } finally {
            stockService.unlockOrder(request.productId(), token);
        }
    }
}
