"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
  { href: "/dashboard", icon: "◆", label: "대시보드" },
  { href: "/stocks", icon: "▤", label: "종목 상세" },
  { href: "/trade", icon: "⇄", label: "매수 / 매도" },
  { href: "/history", icon: "≡", label: "거래 내역" },
  { href: "/holdings", icon: "▦", label: "보유 종목" },
];

export default function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="sidebar">
      <div>
        <div className="brand">
          블루문<span>.</span>
        </div>
        <div className="brand-sub">근거 있는 투자 기록</div>
      </div>
      <nav>
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(`${item.href}/`);
          return (
            <Link key={item.href} href={item.href} className={`nav-btn${active ? " active" : ""}`}>
              <span className="nav-icon">{item.icon}</span> {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="sidebar-foot">모의투자 계좌</div>
    </aside>
  );
}
