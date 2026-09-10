package com.bluemoon.backend.service;

import com.bluemoon.backend.common.exception.ApiException;
import com.bluemoon.backend.common.exception.ErrorCode;
import com.bluemoon.backend.domain.account.Account;
import com.bluemoon.backend.domain.holding.Holding;
import com.bluemoon.backend.domain.order.Order;
import com.bluemoon.backend.domain.order.OrderSide;
import com.bluemoon.backend.domain.order.OrderType;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.dto.request.CreateOrderRequest;
import com.bluemoon.backend.dto.response.CreateOrderResponse;
import com.bluemoon.backend.dto.response.OrderHistoryResponse;
import com.bluemoon.backend.dto.response.PageResponse;
import com.bluemoon.backend.dto.response.TradeResponse;
import com.bluemoon.backend.mapper.AccountMapper;
import com.bluemoon.backend.mapper.HoldingMapper;
import com.bluemoon.backend.mapper.OrderMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 매수/매도 핵심 로직. ACCOUNTS.cash_balance, HOLDINGS.quantity 갱신은 항상 이 클래스를 통해서만 이루어지며,
 * 락 획득 순서는 ACCOUNTS -> HOLDINGS로 고정한다 (데드락 방지, CLAUDE.md 절대 규칙 / docs/db-schema.md 4~5장).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final AccountMapper accountMapper;
    private final HoldingMapper holdingMapper;
    private final StockMapper stockMapper;
    private final OrderMapper orderMapper;

    @Transactional
    public CreateOrderResponse placeOrder(Long userId, CreateOrderRequest request) {
        if (request.orderType() == OrderType.LIMIT && request.limitPrice() == null) {
            throw new ApiException(ErrorCode.INVALID_ORDER_TYPE);
        }

        Stock stock = stockMapper.findByCode(request.stockCode())
                .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));

        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));
        // 1) ACCOUNTS 행 잠금
        account = accountMapper.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        // 2) HOLDINGS 행 잠금 (없으면 생성)
        Holding holding = getOrCreateHoldingForUpdate(account.getId(), request.stockCode());

        BigDecimal filledPrice = request.orderType() == OrderType.LIMIT ? request.limitPrice() : stock.getCurrentPrice();
        BigDecimal totalAmount = filledPrice.multiply(BigDecimal.valueOf(request.quantity()));

        if (request.side() == OrderSide.BUY) {
            if (!account.hasEnoughBalance(totalAmount)) {
                throw new ApiException(ErrorCode.INSUFFICIENT_BALANCE);
            }
            account.debit(totalAmount);
            holding.applyBuy(request.quantity(), filledPrice);
        } else {
            if (!holding.hasEnoughQuantity(request.quantity())) {
                throw new ApiException(ErrorCode.INSUFFICIENT_HOLDING);
            }
            holding.applySell(request.quantity());
            account.credit(totalAmount);
        }

        accountMapper.update(account);
        holdingMapper.update(holding);

        Order order = Order.filled(
                account.getId(), request.stockCode(), request.side(), request.orderType(),
                request.quantity(), request.limitPrice(), filledPrice
        );
        orderMapper.insert(order);

        return new CreateOrderResponse(order.getId(), filledPrice, request.quantity(), totalAmount, account.getCashBalance());
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

    @Transactional(readOnly = true)
    public PageResponse<OrderHistoryResponse> getOrderHistory(Long userId, String stockCode, int page, int size) {
        Account account = accountMapper.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.ACCOUNT_NOT_FOUND));

        int offset = page * size;
        List<Order> orders = stockCode == null
                ? orderMapper.findByAccountId(account.getId(), offset, size)
                : orderMapper.findByAccountIdAndStockCode(account.getId(), stockCode, offset, size);
        long totalElements = stockCode == null
                ? orderMapper.countByAccountId(account.getId())
                : orderMapper.countByAccountIdAndStockCode(account.getId(), stockCode);

        Map<String, Stock> stocksByCode = stockMapper.findAllByCodes(orders.stream().map(Order::getStockCode).distinct().toList())
                .stream()
                .collect(java.util.stream.Collectors.toMap(Stock::getCode, Function.identity()));

        List<OrderHistoryResponse> content = orders.stream()
                .map(o -> new OrderHistoryResponse(
                        o.getId(), o.getStockCode(), stocksByCode.get(o.getStockCode()).getName(),
                        o.getSide(), o.getFilledQuantity(), o.getFilledPrice(), o.getOrderedAt()
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
