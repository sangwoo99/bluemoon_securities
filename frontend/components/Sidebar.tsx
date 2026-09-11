"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

const NAV_ITEMS = [
  { href: "/dashboard", icon: "◆", label: "대시보드" },
  { href: "/stocks", icon: "▤", label: "종목 목록" },
  { href: "/trade", icon: "⇄", label: "매수 / 매도" },
  { href: "/history", icon: "≡", label: "거래 내역" },
  { href: "/holdings", icon: "▦", label: "보유 종목" },
  { href: "/mypage", icon: "◎", label: "마이페이지" },
];

export default function Sidebar() {
  const pathname = usePathname();
  const router = useRouter();

  function handleLogout() {
    document.cookie = "accessToken=; path=/; max-age=0";
    router.push("/login");
    router.refresh();
  }

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
      <div className="sidebar-foot">
        모의투자 계좌
        <div style={{ marginTop: 6 }}>
          <button type="button" className="sidebar-logout" onClick={handleLogout}>
            로그아웃
          </button>
        </div>
      </div>
    </aside>
  );
}
