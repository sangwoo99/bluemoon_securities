"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

const NAV_ITEMS = [
  { href: "/dashboard", icon: "◆", label: "대시보드" },
  { href: "/stocks", icon: "▤", label: "종목 목록" },
  { href: "/trade", icon: "⇄", label: "매수 / 매도" },
  { href: "/history", icon: "≡", label: "거래 내역" },
  { href: "/holdings", icon: "▦", label: "보유·관심 종목" },
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
        <a
          href="https://github.com/sangwoo99/bluemoon_securities"
          target="_blank"
          rel="noopener noreferrer"
          className="sidebar-github"
        >
          <svg viewBox="0 0 16 16" width="15" height="15" fill="currentColor" aria-hidden="true">
            <path d="M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8Z" />
          </svg>
          GitHub 저장소
        </a>
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
