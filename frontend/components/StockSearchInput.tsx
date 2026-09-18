"use client";

import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";

const DEBOUNCE_MS = 300;

export default function StockSearchInput({ initialQuery }: { initialQuery: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [value, setValue] = useState(initialQuery);

  useEffect(() => {
    setValue(initialQuery);
  }, [initialQuery]);

  useEffect(() => {
    if (value === initialQuery) return;
    const timer = setTimeout(() => {
      const params = new URLSearchParams(searchParams.toString());
      if (value.trim()) {
        params.set("q", value.trim());
      } else {
        params.delete("q");
      }
      params.set("page", "1");
      router.push(`/stocks?${params.toString()}`);
    }, DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [value, initialQuery, router, searchParams]);

  return (
    <input
      type="text"
      className="search-input"
      placeholder="종목명 또는 코드로 검색"
      value={value}
      onChange={(e) => setValue(e.target.value)}
    />
  );
}
