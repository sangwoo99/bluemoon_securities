package com.bluemoon.backend.service;

import com.bluemoon.backend.domain.order.PendingOrder;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.mapper.PendingOrderMapper;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 지정가 대기 주문(PENDING_ORDERS)을 시세와 대조해 조건을 만족하면 체결시키는 매칭 배치.
 * 별도 스케줄을 두지 않고 {@link PriceUpdateBatchService}가 시세를 갱신한 직후 호출한다 —
 * 시세가 바뀌는 유일한 시점이 그 배치이므로, 그때마다 재평가하면 충분하고 별도 폴링이 필요 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderMatchingBatchService {

    private final PendingOrderMapper pendingOrderMapper;
    private final StockMapper stockMapper;
    private final OrderService orderService;

    public void matchPendingOrders() {
        List<PendingOrder> pendings = pendingOrderMapper.findAllPending();
        if (pendings.isEmpty()) {
            return;
        }

        Map<String, Stock> stocksByCode = stockMapper.findAllByCodes(
                pendings.stream().map(PendingOrder::getStockCode).distinct().toList()
        ).stream().collect(Collectors.toMap(Stock::getCode, Function.identity()));

        for (PendingOrder pending : pendings) {
            Stock stock = stocksByCode.get(pending.getStockCode());
            if (stock == null || !pending.matches(stock.getCurrentPrice())) {
                continue;
            }
            try {
                orderService.fillPendingOrder(pending.getId());
            } catch (Exception e) {
                log.warn("지정가 주문 체결 실패 — pendingOrderId={}", pending.getId(), e);
            }
        }
    }
}
