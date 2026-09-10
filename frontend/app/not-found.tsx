import Link from "next/link";

export default function NotFound() {
  return (
    <section>
      <div className="empty-state">
        <div className="icon">🔍</div>
        페이지 또는 종목을 찾을 수 없습니다. <Link href="/dashboard">대시보드로 돌아가기</Link>
      </div>
    </section>
  );
}
