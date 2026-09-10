import { cookies } from "next/headers";
import type { ApiResponse } from "./types";
import { ApiError } from "./api-error";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export { ApiError } from "./api-error";

function authHeader(): Record<string, string> {
  const token = cookies().get("accessToken")?.value;
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function apiGet<T>(path: string): Promise<T | null> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    headers: { ...authHeader() },
    cache: "no-store",
  });
  const body: ApiResponse<T> = await res.json();
  if (!body.success) {
    throw new ApiError(body.error?.code ?? "UNKNOWN", body.error?.message ?? "요청에 실패했습니다.");
  }
  return body.data;
}

export async function apiPost<TRequest, TResponse>(path: string, payload: TRequest): Promise<TResponse | null> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeader() },
    body: JSON.stringify(payload),
  });
  const body: ApiResponse<TResponse> = await res.json();
  if (!body.success) {
    throw new ApiError(body.error?.code ?? "UNKNOWN", body.error?.message ?? "요청에 실패했습니다.");
  }
  return body.data;
}
