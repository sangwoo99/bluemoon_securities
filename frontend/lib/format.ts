export const fmtWon = (n: number) => `${n.toLocaleString("ko-KR")}원`;
export const fmtSignedWon = (n: number) => `${n >= 0 ? "+" : ""}${n.toLocaleString("ko-KR")}원`;
export const fmtPct = (n: number) => `${n >= 0 ? "+" : ""}${n.toFixed(2)}%`;
export const gainClass = (n: number) => (n >= 0 ? "up" : "down");
export const gainArrow = (n: number) => (n >= 0 ? "▲" : "▼");
