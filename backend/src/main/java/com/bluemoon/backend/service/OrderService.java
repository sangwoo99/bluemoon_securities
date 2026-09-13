package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.holding.Holding;
import com.bluemoon.backend.domain.order.Order;
import com.bluemoon.backend.domain.order.OrderSide;
import com.bluemoon.backend.domain.order.OrderStatus;
import com.bluemoon.backend.domain.order.OrderType;
import com.bluemoon.backend.domain.order.PendingOrder;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.dto.request.CreateOrderRequest;
import com.bluemoon.backend.dto.response.CancelOrderResponse;
import com.bluemoon.backend.dto.response.CreateOrderResponse;
import com.bluemoon.backend.dto.response.OrderHistoryResponse;
import com.bluemoon.backend.dto.response.PageResponse;
import com.bluemoon.backend.dto.response.TradeResponse;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.HoldingMapper;
import com.bluemoon.backend.mapper.OrderMapper;
import com.bluemoon.backend.mapper.PendingOrderMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * 매수/매도 핵심 로직.
 *
 * 실제 증권사와 동일하게 주문과 체결을 구분한다:
 * - 시장가 주문은 현재가로 즉시 체결된다.
 * - 지정가 주문은 즉시 조건(매수는 현재가&lt;=지정가, 매도는 현재가&gt;=지정가)을 만족하면 바로 체결되고,
 *   그렇지 않으면 PENDING_ORDERS에 대기 티켓으로 쌓여 {@link OrderMatchingBatchService}가 시세 갱신마다 재평가한다.
 * - 취소는 체결 전(PENDING) 상태에서만 가능하다. 이미 체결된 거래는 취소할 수 없다(ORDERS는 append-only).
 *
 * ACCOUNTS.cash_balance, HOLDINGS.quantity 갱신은 항상 이 클래스를 통해서만 이루어지며,
 * 락 획득 순서는 ACCOUNTS -> HOLDINGS로 고정한다 (데드락 방지, CLAUDE.md 절대 규칙 / docs/db-schema.md 4~5장).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final StockMapper stockMapper;
    private final OrderMapper orderMapper;
    private final PendingOrderMapper pendingOrderMapper;

    @Transactional
    public CreateOrderResponse placeOrder(Long userId, CreateOrderRequest request) {
        if (request.orderType() == OrderType.LIMIT && request.limitPrice() == null) {
            throw new ApiException(ErrorCode.INVALID_ORDER_TYPE);
        }
        stockMapper.findByCode(request.stockCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        return request.orderType() == OrderType.LIMIT ? placeLimitOrder(userId, request) : placeMarketOrder(userId, request);
    }

    private CreateOrderResponse placeMarketOrder(Long userId, CreateOrderRequest request) {
        Stock stock = stockMapper.findByCode(request.stockCode()).orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        Account account = lockAccount(userId);
        Holding holding = getOrCreateHoldingForUpdate(account.getId(), request.stockCode());

        Order order = executeFill(account, holding, request.side(), OrderType.MARKET, request.stockCode(),
                request.quantity(), null, stock.getCurrentPrice());

        return new CreateOrderResponse(order.getId(), OrderStatus.FILLED, order.getFilledPrice(),
                order.getFilledQuantity(), order.getTotalAmount(), account.getCashBalance());
    }

    private CreateOrderResponse placeLimitOrder(Long userId, CreateOrderRequest request) {
        Stock stock = stockMapper.findByCode(request.stockCode()).orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
        Account account = lockAccount(userId);
        BigDecimal totalAmount = request.limitPrice().multiply(BigDecimal.valueOf(request.quantity()));

        if (request.side() == OrderSide.BUY) {
            BigDecimal reserved = pendingOrderMapper.sumReservedCash(account.getId());
            if (account.getCashBalance().subtract(reserved).compareTo(totalAmount) < 0) {
                throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
            }
        } else {
            Holding holding = getOrCreateHoldingForUpdate(account.getId(), request.stockCode());
            long reservedQuantity = pendingOrderMapper.sumReservedQuantity(account.getId(), request.stockCode());
            if (holding.getQuantity() - reservedQuantity < request.quantity()) {
                throw new ApiException(ErrorCode.INSUFFICIENT_HOLDING);
            }
        }

        PendingOrder pending = PendingOrder.create(account.getId(), request.stockCode(), request.side(), request.quantity(), request.limitPrice());

        // 지정가가 이미 현재가 기준으로 조건을 만족하면(예: 현재가보다 높게 매수 지정가를 낸 경우) 실제 거래소처럼 즉시 체결한다.
        if (pending.matches(stock.getCurrentPrice())) {
            Holding holding = getOrCreateHoldingForUpdate(account.getId(), request.stockCode());
            Order order = executeFill(account, holding, request.side(), OrderType.LIMIT, request.stockCode(),
                    request.quantity(), request.limitPrice(), request.limitPrice());
            return new CreateOrderResponse(order.getId(), OrderStatus.FILLED, order.getFilledPrice(),
                    order.getFilledQuantity(), order.getTotalAmount(), account.getCashBalance());
        }

        pendingOrderMapper.insert(pending);
        return new CreateOrderResponse(pending.getId(), OrderStatus.PENDING, request.limitPrice(),
                request.quantity(), totalAmount, account.getCashBalance());
    }

    /**
     * 매칭 배치({@link OrderMatchingBatchService})가 시세 조건을 만족한 지정가 대기 주문을 체결 처리한다.
     * PENDING_ORDERS 행 자체를 락으로 잠궈 취소 요청과의 경합을 막는다.
     */
    @Transactional
    public void fillPendingOrder(Long pendingOrderId) {
        PendingOrder pending = pendingOrderMapper.findByIdForUpdate(pendingOrderId).orElse(null);
        if (pending == null || pending.getStatus() != OrderStatus.PENDING) {
            return; // 이미 취소되었거나 처리된 주문 — 배치 중복 실행 등에 대한 방어
        }

        Stock stock = stockMapper.findByCode(pending.getStockCode()).orElse(null);
        if (stock == null || !pending.matches(stock.getCurrentPrice())) {
            return; // 배치 실행 사이에 다시 조건을 벗어난 경우 — 다음 배치에서 재평가
        }

        Account account = accountMapper.findByIdForUpdate(pending.getAccountId())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        Holding holding = getOrCreateHoldingForUpdate(account.getId(), pending.getStockCode());

        try {
            Order order = executeFill(account, holding, pending.getSide(), OrderType.LIMIT, pending.getStockCode(),
                    pending.getQuantity(), pending.getLimitPrice(), pending.getLimitPrice());
            pending.markFilled(order.getId());
            pendingOrderMapper.update(pending);
        } catch (ApiException e) {
            // 주문 접수 시 검증했지만 그 사이 잔고/수량이 바뀌어 체결이 불가능한 경우 — 대기 상태를 유지하고 다음 배치에서 재시도.
        }
    }

    /** 체결(잔고/보유수량 반영 + ORDERS 원장 기록)을 실제로 수행하는 공통 로직. 호출 전 ACCOUNTS -> HOLDINGS 락이 걸려 있어야 한다. */
    private Order executeFill(Account account, Holding holding, OrderSide side, OrderType orderType,
                               String stockCode, long quantity, BigDecimal limitPriceForRecord, BigDecimal fillPrice) {
        BigDecimal totalAmount = fillPrice.multiply(BigDecimal.valueOf(quantity));

        if (side == OrderSide.BUY) {
            if (!account.hasEnoughBalance(totalAmount)) {
                throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
            }
            account.debit(totalAmount);
            holding.applyBuy(quantity, fillPrice);
        } else {
            if (!holding.hasEnoughQuantity(quantity)) {
                throw new ApiException(ErrorCode.INSUFFICIENT_HOLDING);
            }
            holding.applySell(quantity);
            account.credit(totalAmount);
        }

        accountMapper.update(account);
        holdingMapper.update(holding);

        Order order = Order.filled(account.getId(), stockCode, side, orderType, quantity, limitPriceForRecord, fillPrice);
        orderMapper.insert(order);
        return order;
    }

    /** 체결 전(PENDING) 주문만 취소할 수 있다. 이미 체결된 거래는 ORDERS가 append-only라 취소할 수 없다. */
    @Transactional
    public CancelOrderResponse cancelOrder(Long userId, Long pendingOrderId) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        PendingOrder pending = pendingOrderMapper.findByIdAndAccountIdForUpdate(pendingOrderId, account.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        if (pending.getStatus() == OrderStatus.CANCELLED) {
            throw new ApiException(ErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if (pending.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.ORDER_NOT_CANCELABLE);
        }

        pending.markCancelled();
        pendingOrderMapper.update(pending);

        return new CancelOrderResponse(pending.getId(), account.getCashBalance());
    }

    private Account lockAccount(Long userId) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        return accountMapper.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private Holding getOrCreateHoldingForUpdate(Long accountId, String stockCode) {
        return holdingMapper.findByAccountIdAndStockCodeForUpdate(accountId, stockCode)
                .orElseGet(() -> createHolding(accountId, stockCode));
    }

    private Holding createHolding(Long accountId, String stockCode) {
        try {
            Holding holding = new Holding(accountId, stockCode);
            holdingMapper.insert(holding);
            return holding;
        } catch (DuplicateKeyException e) {
            // 동시에 같은 종목을 처음 매수하는 요청과 경합한 경우 — unique(account_id, stock_code)가 막아준 뒤 재조회
            return holdingMapper.findByAccountIdAndStockCodeForUpdate(accountId, stockCode)
                    .orElseThrow(() -> e);
        }
    }

    /** 체결 완료(ORDERS) + 미체결/취소(PENDING_ORDERS, 이미 체결된 건은 중복 표시 방지를 위해 제외)를 시간순으로 합쳐 페이지네이션한다. */
    @Transactional(readOnly = true)
    public PageResponse<OrderHistoryResponse> getOrderHistory(Long userId, String stockCode, int page, int size) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        List<Order> filledOrders = stockCode == null
                ? orderMapper.findAllByAccountId(account.getId())
                : orderMapper.findAllByAccountIdAndStockCode(account.getId(), stockCode);
        List<PendingOrder> openOrCancelled = stockCode == null
                ? pendingOrderMapper.findVisibleByAccountId(account.getId())
                : pendingOrderMapper.findVisibleByAccountIdAndStockCode(account.getId(), stockCode);

        record Row(Long id, String stockCode, OrderSide side, Long quantity, BigDecimal price,
                   LocalDateTime orderedAt, OrderStatus status, boolean cancelable) {
        }

        List<Row> rows = Stream.concat(
                        filledOrders.stream().map(o -> new Row(o.getId(), o.getStockCode(), o.getSide(), o.getFilledQuantity(),
                                o.getFilledPrice(), o.getOrderedAt(), OrderStatus.FILLED, false)),
                        openOrCancelled.stream().map(p -> new Row(p.getId(), p.getStockCode(), p.getSide(), p.getQuantity(),
                                p.getLimitPrice(), p.getOrderedAt(), p.getStatus(), p.getStatus() == OrderStatus.PENDING))
                )
                .sorted(Comparator.comparing(Row::orderedAt).reversed())
                .toList();

        long totalElements = rows.size();
        int fromIndex = Math.min(page * size, rows.size());
        int toIndex = Math.min(fromIndex + size, rows.size());
        List<Row> pageRows = rows.subList(fromIndex, toIndex);

        List<String> stockCodes = pageRows.stream().map(Row::stockCode).distinct().toList();
        Map<String, Stock> stocksByCode = stockCodes.isEmpty()
                ? Map.of()
                : stockMapper.findAllByCodes(stockCodes).stream()
                        .collect(java.util.stream.Collectors.toMap(Stock::getCode, Function.identity()));

        List<OrderHistoryResponse> content = pageRows.stream()
                .map(r -> new OrderHistoryResponse(
                        r.id(), r.stockCode(), stocksByCode.get(r.stockCode()).getName(),
                        r.side(), r.quantity(), r.price(), r.orderedAt(), r.status(), r.cancelable()
                ))
                .toList();

        return new PageResponse<>(content, page, size, totalElements);
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getTradesForStock(Long userId, String stockCode) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        return orderMapper.findAllByAccountIdAndStockCode(account.getId(), stockCode).stream()
                .map(o -> new TradeResponse(o.getId(), o.getSide(), o.getFilledQuantity(), o.getFilledPrice(), o.getOrderedAt()))
                .toList();
    }
}
