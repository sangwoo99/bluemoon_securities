package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.domain.user.User;
import com.bluemoon.backend.dto.request.CreateOrderRequest;
import com.bluemoon.backend.dto.response.CreateOrderResponse;
import com.bluemoon.backend.domain.order.OrderSide;
import com.bluemoon.backend.domain.order.OrderType;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.StockMapper;
import com.bluemoon.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AccountMapper accountMapper;
    @Autowired
    private StockMapper stockMapper;

    private Long userId;
    private final String stockCode = "005930";

    @BeforeEach
    void setUp() {
        User user = new User("tester@example.com", "hash", "테스터");
        userMapper.insert(user);
        accountMapper.insert(new Account(user.getId(), BigDecimal.valueOf(1_000_000)));
        userId = user.getId();

        if (stockMapper.findByCode(stockCode).isEmpty()) {
            Stock stock = Stock.seed(stockCode, "삼성전자", "KOSPI", BigDecimal.valueOf(73_800), BigDecimal.valueOf(73_000));
            stockMapper.insert(stock);
        }
    }

    @Test
    void 매수하면_잔고와_체결금액이_정확히_반영된다() {
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.MARKET, 10L, null);

        CreateOrderResponse response = orderService.placeOrder(userId, request);

        assertThat(response.filledQuantity()).isEqualTo(10L);
        assertThat(response.totalAmount()).isEqualByComparingTo(response.filledPrice().multiply(BigDecimal.valueOf(10)));
        assertThat(response.cashBalanceAfter()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000).subtract(response.totalAmount()));
    }

    @Test
    void 잔고보다_큰_금액을_매수하면_INSUFFICIENT_BALANCE() {
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.MARKET, 1_000_000L, null);

        assertThatThrownBy(() -> orderService.placeOrder(userId, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE));
    }

    @Test
    void 보유하지_않은_종목을_매도하면_INSUFFICIENT_HOLDING() {
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.SELL, OrderType.MARKET, 1L, null);

        assertThatThrownBy(() -> orderService.placeOrder(userId, request))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_HOLDING));
    }

    @Test
    void 동시에_여러_매수_요청이_들어와도_잔고_정합성이_유지된다() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    orderService.placeOrder(userId, new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.MARKET, 1L, null));
                    successCount.incrementAndGet();
                } catch (ApiException ignored) {
                    // 잔고 소진으로 인한 정상적인 실패는 허용
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        Account account = accountMapper.findByUserId(userId).orElseThrow();
        Stock stock = stockMapper.findByCode(stockCode).orElseThrow();
        BigDecimal expected = BigDecimal.valueOf(1_000_000).subtract(stock.getCurrentPrice().multiply(BigDecimal.valueOf(successCount.get())));
        assertThat(account.getCashBalance()).isEqualByComparingTo(expected);
    }
}
