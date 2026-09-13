package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.domain.user.User;
import com.bluemoon.backend.dto.request.CreateOrderRequest;
import com.bluemoon.backend.dto.response.CancelOrderResponse;
import com.bluemoon.backend.dto.response.CreateOrderResponse;
import com.bluemoon.backend.domain.order.OrderSide;
import com.bluemoon.backend.domain.order.OrderStatus;
import com.bluemoon.backend.domain.order.OrderType;
import com.bluemoon.backend.domain.order.PendingOrder;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.PendingOrderMapper;
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
    @Autowired
    private PendingOrderMapper pendingOrderMapper;
    @Autowired
    private OrderMatchingBatchService orderMatchingBatchService;

    private Long userId;
    private final String stockCode = "005930";

    @BeforeEach
    void setUp() {
        // H2 인메모리 DB(DB_CLOSE_DELAY=-1)가 테스트 메서드 사이에 유지되므로 이메일은 매번 고유해야 하고,
        // 다른 테스트가 바꿔놓은 STOCKS.current_price도 매번 알려진 기준값으로 되돌려야 한다.
        User user = new User("tester+" + System.nanoTime() + "@example.com", "hash", "테스터");
        userMapper.insert(user);
        accountMapper.insert(new Account(user.getId(), BigDecimal.valueOf(1_000_000)));
        userId = user.getId();

        Stock stock = stockMapper.findByCode(stockCode).orElse(null);
        if (stock == null) {
            stockMapper.insert(Stock.seed(stockCode, "삼성전자", "KOSPI", BigDecimal.valueOf(73_800), BigDecimal.valueOf(73_000)));
        } else {
            stock.updatePrice(BigDecimal.valueOf(73_800));
            stockMapper.update(stock);
        }
    }

    @Test
    void 매수하면_잔고와_체결금액이_정확히_반영된다() {
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.MARKET, 10L, null);

        CreateOrderResponse response = orderService.placeOrder(userId, request);

        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(response.quantity()).isEqualTo(10L);
        assertThat(response.totalAmount()).isEqualByComparingTo(response.price().multiply(BigDecimal.valueOf(10)));
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

    @Test
    void 현재가보다_불리한_지정가_매수는_체결되지_않고_대기_상태로_쌓인다() {
        // 현재가(73,800)보다 낮은 가격에 매수를 걸었으니 즉시 체결될 이유가 없다.
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.LIMIT, 10L, BigDecimal.valueOf(70_000));

        CreateOrderResponse response = orderService.placeOrder(userId, request);

        assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        Account account = accountMapper.findByUserId(userId).orElseThrow();
        assertThat(account.getCashBalance()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000)); // 체결 전이라 현금은 그대로
    }

    @Test
    void 현재가보다_유리한_지정가_매수는_즉시_체결된다() {
        // 현재가(73,800)보다 높게 매수를 걸었다면 실제 거래소처럼 그 자리에서 바로 체결되어야 한다.
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.LIMIT, 10L, BigDecimal.valueOf(80_000));

        CreateOrderResponse response = orderService.placeOrder(userId, request);

        assertThat(response.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(response.price()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
        Account account = accountMapper.findByUserId(userId).orElseThrow();
        assertThat(account.getCashBalance()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000).subtract(BigDecimal.valueOf(800_000)));
    }

    @Test
    void 대기_주문은_체결되기_전에만_취소할_수_있다() {
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.LIMIT, 10L, BigDecimal.valueOf(70_000));
        CreateOrderResponse placed = orderService.placeOrder(userId, request);
        assertThat(placed.status()).isEqualTo(OrderStatus.PENDING);

        CancelOrderResponse cancelled = orderService.cancelOrder(userId, placed.orderId());
        assertThat(cancelled.orderId()).isEqualTo(placed.orderId());

        assertThatThrownBy(() -> orderService.cancelOrder(userId, placed.orderId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_ALREADY_CANCELLED));
    }

    @Test
    void 매칭_배치가_지정가_조건을_만족하면_대기_주문을_체결한다() {
        CreateOrderRequest request = new CreateOrderRequest(stockCode, OrderSide.BUY, OrderType.LIMIT, 10L, BigDecimal.valueOf(70_000));
        CreateOrderResponse placed = orderService.placeOrder(userId, request);
        assertThat(placed.status()).isEqualTo(OrderStatus.PENDING);

        // 시세가 지정가 이하로 내려오면 다음 배치에서 체결되어야 한다.
        Stock stock = stockMapper.findByCode(stockCode).orElseThrow();
        stock.updatePrice(BigDecimal.valueOf(65_000));
        stockMapper.update(stock);

        orderMatchingBatchService.matchPendingOrders();

        PendingOrder pending = pendingOrderMapper.findByIdForUpdate(placed.orderId()).orElseThrow();
        assertThat(pending.getStatus()).isEqualTo(OrderStatus.FILLED);

        // 지정가 그대로(70,000) 체결되어야 한다 — 그 사이 더 떨어진 현재가(65,000)가 아니다.
        Account account = accountMapper.findByUserId(userId).orElseThrow();
        assertThat(account.getCashBalance()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000).subtract(BigDecimal.valueOf(700_000)));

        // 취소는 더 이상 불가능하다 — 이미 체결됨
        assertThatThrownBy(() -> orderService.cancelOrder(userId, placed.orderId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_NOT_CANCELABLE));
    }
}
