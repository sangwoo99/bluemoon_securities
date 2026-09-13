"use client";

import { useEffect, useRef, useState } from "react";

/**
 * recharts ResponsiveContainer는 ResizeObserver 콜백에 의존하는데, 그게 안 도는 환경(백그라운드 탭 등)에서는
 * 영영 크기를 못 재 차트가 안 그려진다. 마운트 시점에 getBoundingClientRect로 즉시 크기를 읽어와 우회한다.
 */
export function useElementSize<T extends HTMLElement>() {
  const ref = useRef<T | null>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });

  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const measure = () => {
      const rect = el.getBoundingClientRect();
      setSize({ width: rect.width, height: rect.height });
    };

    measure();

    const observer = new ResizeObserver(measure);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  return { ref, width: size.width, height: size.height };
}
