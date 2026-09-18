"use client";

import { useCallback, useRef, useState } from "react";

/**
 * recharts ResponsiveContainer는 ResizeObserver 콜백에 의존하는데, 그게 안 도는 환경(백그라운드 탭 등)에서는
 * 영영 크기를 못 재 차트가 안 그려진다. 마운트 시점에 getBoundingClientRect로 즉시 크기를 읽어와 우회한다.
 *
 * 콜백 ref로 구현한다 — object ref + useEffect(fn, [])였을 때는, 데이터가 없어졌다 다시 생기는 것처럼
 * 같은 컴포넌트 인스턴스 안에서 이 엘리먼트가 있다/없다를 오가면(예: PriceChart가 data.length===0일 때
 * 다른 DOM 트리를 반환) 새로 생긴 DOM 노드에 effect가 다시 붙지 않아 크기가 영원히 0으로 굳어버렸다.
 * 콜백 ref는 노드가 붙거나 떨어질 때마다 매번 호출되므로 이 문제가 없다.
 */
export function useElementSize<T extends HTMLElement>() {
  const [size, setSize] = useState({ width: 0, height: 0 });
  const observerRef = useRef<ResizeObserver | null>(null);

  const ref = useCallback((el: T | null) => {
    observerRef.current?.disconnect();
    observerRef.current = null;
    if (!el) return;

    const measure = () => {
      const rect = el.getBoundingClientRect();
      setSize({ width: rect.width, height: rect.height });
    };

    measure();

    const observer = new ResizeObserver(measure);
    observer.observe(el);
    observerRef.current = observer;
  }, []);

  return { ref, width: size.width, height: size.height };
}
