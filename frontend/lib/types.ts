export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  error: { code: string; message: string } | null;
}

export interface PortfolioSummary {
  totalValue: number;
  totalCost: number;
  totalGain: number;
  totalGainRate: number;
  todayChange: number;
  todayChangeRate: number;
  holdingCount: number;
}

export interface TrendPoint {
  date: string;
  value: number;
}

export type TrendPeriod = "1W" | "1M" | "3M" | "1Y";

export interface Holding {
  stockCode: string;
  stockName: string;
  quantity: number;
  avgPrice: number;
  currentPrice: number;
  evalValue: number;
  evalGain: number;
  evalGainRate: number;
}

export interface StockDetail {
  code: string;
  name: string;
  market: "KOSPI" | "KOSDAQ";
  currentPrice: number;
  prevClose: number;
}

export interface PricePoint {
  date: string;
  price: number;
}

export type OrderSide = "BUY" | "SELL";
export type OrderType = "MARKET" | "LIMIT";

export interface Trade {
  orderId: number;
  side: OrderSide;
  quantity: number;
  price: number;
  orderedAt: string;
}

export type OrderStatus = "PENDING" | "FILLED" | "CANCELLED";

export interface OrderHistoryItem {
  orderId: number;
  stockCode: string;
  stockName: string;
  side: OrderSide;
  quantity: number;
  price: number;
  orderedAt: string;
  status: OrderStatus;
  cancelable: boolean;
}

export interface CancelOrderResponse {
  orderId: number;
  cashBalanceAfter: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface CreateOrderRequest {
  stockCode: string;
  side: OrderSide;
  orderType: OrderType;
  quantity: number;
  limitPrice: number | null;
}

export interface CreateOrderResponse {
  orderId: number;
  status: OrderStatus;
  price: number;
  quantity: number;
  totalAmount: number;
  cashBalanceAfter: number;
}

export interface InsightSource {
  name: string;
  date: string;
  title?: string;
  url?: string;
}

export interface Insight {
  stockCode?: string;
  stockName?: string;
  content: string;
  sources: InsightSource[];
  generatedAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface SignupRequest {
  email: string;
  password: string;
  name: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
}

export interface SignupResponse {
  userId: number;
  accountId: number;
}

export interface UserProfile {
  email: string;
  name: string;
  createdAt: string;
  cashBalance: number;
  accountCreatedAt: string;
}
