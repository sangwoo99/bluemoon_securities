"use client";

import { useState } from "react";
import AiInsightCard from "@/components/AiInsightCard";
import type { Insight } from "@/lib/types";

export default function AiInsightCarousel({ insights }: { insights: Insight[] }) {
  const [index, setIndex] = useState(0);

  if (insights.length === 0) {
    return <AiInsightCard insight={null} badgeLabel="오늘의 AI 인사이트" />;
  }

  const currentIndex = Math.min(index, insights.length - 1);
  const current = insights[currentIndex];

  const nav = insights.length > 1 && (
    <div className="ai-carousel-nav">
      <button
        type="button"
        className="ai-carousel-arrow"
        onClick={() => setIndex((i) => (i - 1 + insights.length) % insights.length)}
        aria-label="이전 종목"
      >
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
      <button
        type="button"
        className="ai-carousel-arrow"
        onClick={() => setIndex((i) => (i + 1) % insights.length)}
        aria-label="다음 종목"
      >
        ›
      </button>
    </div>
  );

  return (
    <AiInsightCard
      insight={current}
      generatedAtLabel={`${current.generatedAt.slice(11, 16)} 생성`}
      linkStock
      badgeLabel="오늘의 AI 인사이트"
      nav={nav}
    />
  );
}
