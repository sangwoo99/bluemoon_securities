package com.bluemoon.backend.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 배포 컨테이너의 JVM 기본 타임존이 KST가 아닐 수 있어(예: 운영 Docker 이미지는 UTC 기본값),
 * "오늘" 날짜가 걸리는 모든 곳에서 시스템 기본 타임존 대신 이 클래스를 사용해 Asia/Seoul을 명시한다.
 * {@code @Scheduled(zone = "Asia/Seoul")}은 트리거 시각만 KST로 맞출 뿐 코드 안의 LocalDate.now()에는
 * 영향을 주지 않으므로, 배치 안에서 "오늘"을 구할 때도 반드시 이걸 사용할 것.
 */
public final class KstClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
