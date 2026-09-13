package com.bluemoon.backend.service;

import com.bluemoon.backend.client.KisClient;
import com.bluemoon.backend.domain.stock.Stock;
import com.bluemoon.backend.mapper.StockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 장중 KIS 시세를 폴링해 STOCKS.current_price를 갱신하는 배치.
 * WebSocket 실시간 스트리밍 대신 REST 폴링을 택한 설계(docs/PRD.md)에 따라, 요청 경로가 아닌 이 배치에서만 KIS를 호출한다.
 * 종목별로 독립된 단건 UPDATE라 @Transactional로 묶지 않는다 — 한 종목 조회 실패가 이미 갱신된
 * 다른 종목까지 롤백시키면 안 되기 때문 (여러 매퍼 호출을 묶어야 하는 주문 처리 등과는 다른 케이스).
 * 시세가 바뀌는 시점이 바로 여기이므로, 갱신이 끝나면 지정가 대기 주문 매칭({@link OrderMatchingBatchService})도 이어서 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceUpdateBatchService {

    private final StockMapper stockMapper;
    private final KisClient kisClient;
    private final OrderMatchingBatchService orderMatchingBatchService;

    /**
     * KIS 모의투자 계좌는 초당 호출 건수 제한이 있어(EGW00201, "초당 거래건수를 초과하였습니다"),
     * 종목 사이에 간격을 둔다. 300ms로는 여전히 걸려서(제한이 초 단위 버킷으로 갱신되는 듯) 1.1초로 설정.
     */
    private static final long CALL_INTERVAL_MS = 1100;

    /** 평일 장중(09~15시) 10분 간격 폴링. KRX 정규장은 09:00~15:30. */
    @Scheduled(cron = "0 */10 9-15 * * MON-FRI", zone = "Asia/Seoul")
    public void updateStockPrices() {
        List<Stock> stocks = stockMapper.findAll();
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            kisClient.getCurrentPrice(stock.getCode()).ifPresentOrElse(
                    price -> {
                        stock.updatePrice(price);
                        stockMapper.update(stock);
                    },
                    () -> log.warn("시세 갱신 건너뜀(조회 실패) — stockCode={}", stock.getCode())
            );

            if (i < stocks.size() - 1) {
                sleep(CALL_INTERVAL_MS);
            }
        }

        orderMatchingBatchService.matchPendingOrders();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
