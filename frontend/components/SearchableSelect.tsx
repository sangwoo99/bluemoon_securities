"use client";

import { useMemo, useRef, useState } from "react";

interface Option {
  code: string;
  name: string;
}

export default function SearchableSelect({
  options,
  value,
  onChange,
  id,
}: {
  options: Option[];
  value: string;
  onChange: (code: string) => void;
  id?: string;
}) {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [highlight, setHighlight] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => o.code === value);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return options;
    return options.filter((o) => o.name.toLowerCase().includes(q) || o.code.toLowerCase().includes(q));
  }, [options, query]);

  function selectOption(option: Option) {
    onChange(option.code);
    setQuery("");
    setOpen(false);
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (!open) {
      if (e.key === "ArrowDown" || e.key === "Enter") {
        setOpen(true);
        setHighlight(0);
      }
      return;
    }
    if (e.key === "ArrowDown") {
      e.preventDefault();
      setHighlight((h) => Math.min(h + 1, filtered.length - 1));
    } else if (e.key === "ArrowUp") {
      e.preventDefault();
      setHighlight((h) => Math.max(h - 1, 0));
    } else if (e.key === "Enter") {
      e.preventDefault();
      if (filtered[highlight]) selectOption(filtered[highlight]);
    } else if (e.key === "Escape") {
      setOpen(false);
    }
  }

  return (
    <div ref={containerRef} style={{ position: "relative" }}>
      <input
        id={id}
        type="text"
        placeholder={selected ? selected.name : "종목명 또는 코드 검색"}
        value={open ? query : ""}
        style={{ paddingRight: 32, cursor: open ? "text" : "pointer" }}
        onChange={(e) => {
          setQuery(e.target.value);
          setOpen(true);
          setHighlight(0);
        }}
        onFocus={() => {
          setQuery("");
          setHighlight(0);
          setOpen(true);
        }}
        onBlur={() => setTimeout(() => setOpen(false), 120)}
        onKeyDown={handleKeyDown}
      />
      <span
        aria-hidden
        style={{
          position: "absolute",
          right: 13,
          top: 0,
          bottom: 0,
          display: "flex",
          alignItems: "center",
          pointerEvents: "none",
          color: "var(--text-faint)",
          fontSize: 20,
          transform: open ? "rotate(180deg)" : undefined,
          transition: "transform 0.15s",
        }}
      >
        ▾
      </span>
      {!open && selected && (
        <div
          style={{
            position: "absolute",
            left: 13,
            top: 0,
            bottom: 0,
            display: "flex",
            alignItems: "center",
            pointerEvents: "none",
            fontFamily: "var(--font-plex-mono), monospace",
            fontSize: 14,
            color: "var(--text)",
          }}
        >
          {selected.name} <span className="mono" style={{ marginLeft: 6, fontSize: 11, color: "var(--text-faint)" }}>{selected.code}</span>
        </div>
      )}
      {open && (
        <div
          style={{
            position: "absolute",
            top: "calc(100% + 4px)",
            left: 0,
            right: 0,
            background: "var(--surface-2)",
            border: "1px solid var(--border)",
            borderRadius: 8,
            maxHeight: 220,
            overflowY: "auto",
            zIndex: 10,
          }}
        >
          {filtered.length === 0 && (
            <div style={{ padding: "10px 13px", fontSize: 13, color: "var(--text-faint)" }}>검색 결과가 없습니다</div>
          )}
          {filtered.map((o, i) => (
            <div
              key={o.code}
              onMouseDown={() => selectOption(o)}
              onMouseEnter={() => setHighlight(i)}
              style={{
                padding: "9px 13px",
                fontSize: 13.5,
                cursor: "pointer",
                background: i === highlight ? "var(--accent-dim)" : "transparent",
                color: i === highlight ? "var(--accent)" : "var(--text)",
                display: "flex",
                justifyContent: "space-between",
              }}
            >
              <span>{o.name}</span>
              <span className="mono" style={{ fontSize: 11, color: "var(--text-faint)" }}>{o.code}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
