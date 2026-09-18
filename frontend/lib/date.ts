/** 주말만 건너뛴 직전 영업일을 "M.d" 형식으로 반환한다. 공휴일은 반영하지 않는다. */
export function prevTradingDayLabel(): string {
  const now = new Date(new Date().toLocaleString("en-US", { timeZone: "Asia/Seoul" }));
  const day = now.getDay(); // 0 = 일, 1 = 월
  const daysBack = day === 0 ? 2 : day === 1 ? 3 : 1;
  now.setDate(now.getDate() - daysBack);
  return `${now.getMonth() + 1}.${now.getDate()}`;
}
