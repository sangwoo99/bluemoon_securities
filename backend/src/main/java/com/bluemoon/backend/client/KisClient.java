package com.bluemoon.backend.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 한국투자증권(KIS) Open API 클라이언트. 시세 조회 전용 — 주문 관련 엔드포인트는 절대 호출하지 않는다
 * (모의투자 앱키를 쓰더라도, 실전 앱키로 잘못 설정된 경우 실제 주문이 나갈 수 있음).
 * 원칙적으로 요청 경로에서는 호출하지 않고, 배치({@link com.bluemoon.backend.service.PriceUpdateBatchService},
 * {@link com.bluemoon.backend.service.PriceHistoryBackfillService})에서만 사용한다.
 * 유일한 예외는 {@link #getIntradayPriceHistory(String)}(당일 분봉) — DB 배치 캐시 없이 이 메서드가 직접
 * KIS를 호출하지만, 호출하는 쪽인 StockService에서 종목당 30분 Redis 캐시를 두고 있어 실제로는 30분에
 * 최대 1번만 불린다(완전한 배치는 아니지만 매 요청마다 호출되는 것도 아님).
 */
@Slf4j
@Component
public class KisClient {

    private static final String TOKEN_CACHE_KEY = "kis:access_token";
    private static final String TOKEN_FAILURE_CACHE_KEY = "kis:access_token:failed";
    private static final Duration TOKEN_FAILURE_BACKOFF = Duration.ofSeconds(60);
    private static final String PRICE_TR_ID = "FHKST01010100";
    private static final String FLUCTUATION_RANK_TR_ID = "FHPST01700000";
    private static final String VOLUME_RANK_TR_ID = "FHPST01710000";
    private static final String DAILY_PRICE_TR_ID = "FHKST03010100";
    private static final String INTRADAY_PRICE_TR_ID = "FHKST03010200";

    private final WebClient webClient;
    private final StringRedisTemplate redisTemplate;
    private final String appKey;
    private final String appSecret;

    public KisClient(
            WebClient kisWebClient,
            StringRedisTemplate redisTemplate,
            @Value("${app.kis.app-key}") String appKey,
            @Value("${app.kis.app-secret}") String appSecret
    ) {
        this.webClient = kisWebClient;
        this.redisTemplate = redisTemplate;
        this.appKey = appKey;
        this.appSecret = appSecret;
    }

    /**
     * 종목의 현재가와 전일대비율을 조회한다. 전일대비율(prdy_ctrt)을 같이 받아와야 prevClose를
     * 역산할 수 있다 — 현재가만 받으면 폴링 때마다 prevClose를 "직전 폴링 가격"으로 잘못 갱신하게 된다.
     * 키 미설정/장애 시 빈 값을 반환한다 (배치가 나머지 종목을 계속 처리할 수 있도록).
     */
    public Optional<CurrentPriceItem> getCurrentPrice(String stockCode) {
        String token = getAccessToken();
        if (token == null) {
            return Optional.empty();
        }

        try {
            PriceResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", PRICE_TR_ID)
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(PriceResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || response.output() == null || !"0".equals(response.rtCd())) {
                log.warn("KIS 시세 조회 실패 — stockCode={}, msg={}", stockCode, response != null ? response.msg1() : "응답 없음");
                return Optional.empty();
            }
            return Optional.of(new CurrentPriceItem(
                    new BigDecimal(response.output().currentPrice()),
                    new BigDecimal(response.output().changeRatePercent())
            ));
        } catch (WebClientResponseException e) {
            log.warn("KIS 시세 조회 중 오류 — stockCode={}, status={}, body={}", stockCode, e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("KIS 시세 조회 중 오류 — stockCode={}, error={}", stockCode, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 국내주식 기간별시세를 조회한다. from~to 구간의 종가를 날짜별로 반환 (최대 100건, KIS 제약).
     * periodDivCode: "D"(일봉)/"W"(주봉)/"M"(월봉) — 긴 기간은 주봉/월봉으로 받아야 100건 제약을 안 넘는다.
     * 과거 시세 백필 전용 — 키 미설정/장애 시 빈 리스트를 반환한다.
     */
    public List<DailyPriceItem> getPriceHistory(String stockCode, LocalDate from, LocalDate to, String periodDivCode) {
        String token = getAccessToken();
        if (token == null) {
            return List.of();
        }

        try {
            DailyPriceResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .queryParam("FID_INPUT_DATE_1", from.format(DateTimeFormatter.BASIC_ISO_DATE))
                            .queryParam("FID_INPUT_DATE_2", to.format(DateTimeFormatter.BASIC_ISO_DATE))
                            .queryParam("FID_PERIOD_DIV_CODE", periodDivCode)
                            .queryParam("FID_ORG_ADJ_PRC", "0")
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", DAILY_PRICE_TR_ID)
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(DailyPriceResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || response.output2() == null || !"0".equals(response.rtCd())) {
                log.warn("KIS 기간별시세 조회 실패 — stockCode={}, msg={}", stockCode, response != null ? response.msg1() : "응답 없음");
                return List.of();
            }
            return response.output2().stream()
                    .filter(o -> o.date() != null && !o.date().isBlank())
                    .map(o -> new DailyPriceItem(LocalDate.parse(o.date(), DateTimeFormatter.BASIC_ISO_DATE), new BigDecimal(o.closePrice())))
                    .toList();
        } catch (WebClientResponseException e) {
            log.warn("KIS 기간별시세 조회 중 오류 — stockCode={}, status={}, body={}", stockCode, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("KIS 기간별시세 조회 중 오류 — stockCode={}, error={}", stockCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 당일 분봉(체결 시각별 가격)을 조회한다. 실제 응답 확인 결과 KIS는 조회 기준시각(FID_INPUT_HOUR_1) 이전
     * 최근 30건까지만 준다(개장부터 전체가 아님) — 그래서 기준시각을 "지금"으로 넘겨 항상 "최근 30분 추이"를
     * 보여주도록 한다(장마감 이후엔 자연스럽게 마감 직전 30분이 됨). 장애/키 미설정 시 빈 리스트.
     */
    public List<IntradayPriceItem> getIntradayPriceHistory(String stockCode) {
        String token = getAccessToken();
        if (token == null) {
            return List.of();
        }

        String nowHourMinuteSecond = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("HHmmss"));

        try {
            IntradayPriceResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                            .queryParam("FID_ETC_CLS_CODE", "")
                            .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                            .queryParam("FID_INPUT_ISCD", stockCode)
                            .queryParam("FID_INPUT_HOUR_1", nowHourMinuteSecond)
                            .queryParam("FID_PW_DATA_INCU_YN", "Y")
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", INTRADAY_PRICE_TR_ID)
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(IntradayPriceResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || response.output2() == null || !"0".equals(response.rtCd())) {
                log.warn("KIS 당일 분봉 조회 실패 — stockCode={}, msg={}", stockCode, response != null ? response.msg1() : "응답 없음");
                return List.of();
            }
            return response.output2().stream()
                    .filter(o -> o.time() != null && !o.time().isBlank())
                    .map(o -> new IntradayPriceItem(o.time(), new BigDecimal(o.price())))
                    .sorted(java.util.Comparator.comparing(IntradayPriceItem::time))
                    .toList();
        } catch (WebClientResponseException e) {
            log.warn("KIS 당일 분봉 조회 중 오류 — stockCode={}, status={}, body={}", stockCode, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("KIS 당일 분봉 조회 중 오류 — stockCode={}, error={}", stockCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 국내주식 등락률 순위(상승률순)를 조회한다. marketInputCode: "0001"(코스피) / "1001"(코스닥).
     * tr_id/파라미터는 모의투자 앱키로 직접 호출해 확인한 값(KIS 개발자센터 문서가 SPA라 정적으로 크롤링이 안 됨).
     * fid_rank_sort_cls_code="0"이 상승률순, "1"이 하락률순이다 — 과거에 이 둘을 반대로 착각해 "1"로 잘못
     * 정정했던 적이 있었는데(그래서 등락률 TOP10에 실제로는 하락률 상위 종목들이 담겼었다), 실제 응답을
     * 다시 검증해 "0"으로 재정정함.
     */
    public List<FluctuationRankItem> getTopFluctuationStocks(String marketInputCode, int count) {
        String token = getAccessToken();
        if (token == null) {
            return List.of();
        }

        try {
            FluctuationRankResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/ranking/fluctuation")
                            .queryParam("fid_cond_mrkt_div_code", "J")
                            .queryParam("fid_cond_scr_div_code", "20170")
                            .queryParam("fid_input_iscd", marketInputCode)
                            .queryParam("fid_rank_sort_cls_code", "0")
                            .queryParam("fid_input_cnt_1", "0")
                            .queryParam("fid_prc_cls_code", "1")
                            .queryParam("fid_input_price_1", "")
                            .queryParam("fid_input_price_2", "")
                            .queryParam("fid_vol_cnt", "")
                            .queryParam("fid_trgt_cls_code", "0")
                            .queryParam("fid_trgt_exls_cls_code", "0")
                            .queryParam("fid_div_cls_code", "0")
                            .queryParam("fid_rsfl_rate1", "")
                            .queryParam("fid_rsfl_rate2", "")
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", FLUCTUATION_RANK_TR_ID)
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(FluctuationRankResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || response.output() == null || !"0".equals(response.rtCd())) {
                log.warn("KIS 등락률 순위 조회 실패 — market={}, msg={}", marketInputCode, response != null ? response.msg1() : "응답 없음");
                return List.of();
            }
            return response.output().stream()
                    .limit(count)
                    .map(o -> new FluctuationRankItem(o.code(), o.name(), new BigDecimal(o.currentPrice()), new BigDecimal(o.changeRatePercent())))
                    .toList();
        } catch (WebClientResponseException e) {
            log.warn("KIS 등락률 순위 조회 중 오류 — market={}, status={}, body={}", marketInputCode, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("KIS 등락률 순위 조회 중 오류 — market={}, error={}", marketInputCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 국내주식 거래량 순위를 조회한다. marketInputCode: "0001"(코스피) / "1001"(코스닥).
     * 등락률 순위와 마찬가지로 모의투자 앱키로 직접 호출해 확인한 값. 시장을 지정하지 않고("0000") 호출하면
     * 레버리지/인버스 ETF가 상위권을 휩쓸어, 개별 종목 위주로 보이도록 시장별 호출만 사용한다.
     */
    public List<VolumeRankItem> getTopVolumeStocks(String marketInputCode, int count) {
        String token = getAccessToken();
        if (token == null) {
            return List.of();
        }

        try {
            VolumeRankResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                            .queryParam("fid_cond_mrkt_div_code", "J")
                            .queryParam("fid_cond_scr_div_code", "20171")
                            .queryParam("fid_input_iscd", marketInputCode)
                            .queryParam("fid_div_cls_code", "0")
                            .queryParam("fid_blng_cls_code", "0")
                            .queryParam("fid_trgt_cls_code", "111111111")
                            .queryParam("fid_trgt_exls_cls_code", "0000000000")
                            .queryParam("fid_input_price_1", "")
                            .queryParam("fid_input_price_2", "")
                            .queryParam("fid_vol_cnt", "")
                            .queryParam("fid_input_date_1", "")
                            .build())
                    .header("authorization", "Bearer " + token)
                    .header("appkey", appKey)
                    .header("appsecret", appSecret)
                    .header("tr_id", VOLUME_RANK_TR_ID)
                    .header("custtype", "P")
                    .retrieve()
                    .bodyToMono(VolumeRankResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || response.output() == null || !"0".equals(response.rtCd())) {
                log.warn("KIS 거래량 순위 조회 실패 — market={}, msg={}", marketInputCode, response != null ? response.msg1() : "응답 없음");
                return List.of();
            }
            return response.output().stream()
                    .limit(count)
                    .map(o -> new VolumeRankItem(
                            o.code(), o.name(), new BigDecimal(o.currentPrice()),
                            new BigDecimal(o.changeRatePercent()), Long.parseLong(o.volume())
                    ))
                    .toList();
        } catch (WebClientResponseException e) {
            log.warn("KIS 거래량 순위 조회 중 오류 — market={}, status={}, body={}", marketInputCode, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("KIS 거래량 순위 조회 중 오류 — market={}, error={}", marketInputCode, e.getMessage());
            return List.of();
        }
    }

    /**
     * 접근토큰은 발급 API 자체가 분당 1회로 제한되어 있어 Redis에 캐싱해 재사용한다.
     * 발급 실패도 {@link #TOKEN_FAILURE_BACKOFF} 동안 Redis에 캐싱한다 — 실패를 캐싱하지 않으면
     * (예: KIS 장애나 앱키 문제로 계속 실패하는 동안) 이 메서드를 호출하는 모든 요청이 매번 다시
     * 발급을 시도하게 되어, 분당 1회 제한을 훨씬 초과해 KIS를 두드리게 되고 결국 앱키가 일시
     * 제한당하는 사태로 이어질 수 있다.
     */
    private synchronized String getAccessToken() {
        String cached = redisTemplate.opsForValue().get(TOKEN_CACHE_KEY);
        if (cached != null) {
            return cached;
        }

        if (redisTemplate.hasKey(TOKEN_FAILURE_CACHE_KEY)) {
            return null;
        }

        try {
            TokenResponse response = webClient.post()
                    .uri("/oauth2/tokenP")
                    .bodyValue(new TokenRequest("client_credentials", appKey, appSecret))
                    .retrieve()
                    .bodyToMono(TokenResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (response == null || response.accessToken() == null) {
                log.warn("KIS 접근토큰 발급 실패");
                redisTemplate.opsForValue().set(TOKEN_FAILURE_CACHE_KEY, "1", TOKEN_FAILURE_BACKOFF);
                return null;
            }

            long ttlSeconds = Math.max((response.expiresIn() != null ? response.expiresIn() : 3600) - 60, 60);
            redisTemplate.opsForValue().set(TOKEN_CACHE_KEY, response.accessToken(), Duration.ofSeconds(ttlSeconds));
            return response.accessToken();
        } catch (Exception e) {
            log.warn("KIS 접근토큰 발급 중 오류 — error={}", e.getMessage());
            redisTemplate.opsForValue().set(TOKEN_FAILURE_CACHE_KEY, "1", TOKEN_FAILURE_BACKOFF);
            return null;
        }
    }

    private record TokenRequest(
            @JsonProperty("grant_type") String grantType,
            String appkey,
            String appsecret
    ) {
    }

    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("expires_in") Long expiresIn
    ) {
    }

    private record PriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            String msg1,
            PriceOutput output
    ) {
    }

    private record PriceOutput(
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("prdy_ctrt") String changeRatePercent
    ) {
    }

    public record CurrentPriceItem(BigDecimal currentPrice, BigDecimal changeRatePercent) {
    }

    public record FluctuationRankItem(String code, String name, BigDecimal currentPrice, BigDecimal changeRatePercent) {
    }

    private record FluctuationRankResponse(
            @JsonProperty("rt_cd") String rtCd,
            String msg1,
            List<FluctuationRankOutput> output
    ) {
    }

    private record FluctuationRankOutput(
            @JsonProperty("stck_shrn_iscd") String code,
            @JsonProperty("hts_kor_isnm") String name,
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("prdy_ctrt") String changeRatePercent
    ) {
    }

    public record VolumeRankItem(String code, String name, BigDecimal currentPrice, BigDecimal changeRatePercent, long volume) {
    }

    private record VolumeRankResponse(
            @JsonProperty("rt_cd") String rtCd,
            String msg1,
            List<VolumeRankOutput> output
    ) {
    }

    /** 거래량 순위는 종목코드 필드명이 등락률 순위와 다르다(mksc_shrn_iscd vs stck_shrn_iscd). */
    private record VolumeRankOutput(
            @JsonProperty("mksc_shrn_iscd") String code,
            @JsonProperty("hts_kor_isnm") String name,
            @JsonProperty("stck_prpr") String currentPrice,
            @JsonProperty("prdy_ctrt") String changeRatePercent,
            @JsonProperty("acml_vol") String volume
    ) {
    }

    public record DailyPriceItem(LocalDate date, BigDecimal closePrice) {
    }

    private record DailyPriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            String msg1,
            List<DailyPriceOutput2> output2
    ) {
    }

    private record DailyPriceOutput2(
            @JsonProperty("stck_bsop_date") String date,
            @JsonProperty("stck_clpr") String closePrice
    ) {
    }

    /** time: "HHMMSS" 형식의 체결 시각 문자열. */
    public record IntradayPriceItem(String time, BigDecimal price) {
    }

    private record IntradayPriceResponse(
            @JsonProperty("rt_cd") String rtCd,
            String msg1,
            List<IntradayPriceOutput2> output2
    ) {
    }

    private record IntradayPriceOutput2(
            @JsonProperty("stck_cntg_hour") String time,
            @JsonProperty("stck_prpr") String price
    ) {
    }
}
