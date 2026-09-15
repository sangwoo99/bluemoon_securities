"use client";

import { useRef, useState } from "react";
import type { TouchEvent } from "react";
import AiInsightCard from "@/components/AiInsightCard";
import type { Insight } from "@/lib/types";

const SWIPE_THRESHOLD_PX = 40;

export default function AiInsightCarousel({ insights }: { insights: Insight[] }) {
  const [index, setIndex] = useState(0);
  const touchStartX = useRef<number | null>(null);

  if (insights.length === 0) {
    return <AiInsightCard insight={null} badgeLabel="오늘의 AI 인사이트" />;
  }

  const currentIndex = Math.min(index, insights.length - 1);
  const current = insights[currentIndex];

  function goPrev() {
    setIndex((i) => (i - 1 + insights.length) % insights.length);
  }
  function goNext() {
    setIndex((i) => (i + 1) % insights.length);
  }

  function handleTouchStart(e: TouchEvent) {
    touchStartX.current = e.touches[0].clientX;
  }
  function handleTouchEnd(e: TouchEvent) {
    if (touchStartX.current == null || insights.length <= 1) return;
    const delta = e.changedTouches[0].clientX - touchStartX.current;
    touchStartX.current = null;
    if (Math.abs(delta) < SWIPE_THRESHOLD_PX) return;
    if (delta < 0) goNext();
    else goPrev();
  }

  const nav = insights.length > 1 && (
    <div className="ai-carousel-nav">
      <button type="button" className="ai-carousel-arrow" onClick={goPrev} aria-label="이전 종목">
        ‹
      </button>
      <div className="ai-carousel-dots">
        {insights.map((insight, i) => (
          <button
            key={insight.stockCode ?? i}
            type="button"
            className={`ai-carousel-dot${i === currentIndex ? " active" : ""}`}
            onClick={() => setIndex(i)}
            aria-label={`${insight.stockName ?? `${i + 1}번째`} 인사이트 보기`}
          />
        ))}
      </div>
      <button type="button" className="ai-carousel-arrow" onClick={goNext} aria-label="다음 종목">
        ›
      </button>
    </div>
  );

  return (
    <div className="ai-carousel-swipe" onTouchStart={handleTouchStart} onTouchEnd={handleTouchEnd}>
      <AiInsightCard
        insight={current}
        generatedAtLabel={`${current.generatedAt.slice(11, 16)} 생성`}
        linkStock
        badgeLabel="오늘의 AI 인사이트"
        nav={nav}
      />
    </div>
  );
}
