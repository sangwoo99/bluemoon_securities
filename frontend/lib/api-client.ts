"use client";

import type { ApiResponse } from "./types";
import { ApiError } from "./api-error";

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

function readCookie(name: string): string | undefined {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : undefined;
}

export async function apiPostClient<TRequest, TResponse>(path: string, payload: TRequest): Promise<TResponse> {
  const token = readCookie("accessToken");
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });
  const body: ApiResponse<TResponse> = await res.json();
  if (!body.success) {
    throw new ApiError(body.error?.code ?? "UNKNOWN", body.error?.message ?? "요청에 실패했습니다.");
  }
  return body.data as TResponse;
}

export async function apiPutClient<TRequest, TResponse>(path: string, payload: TRequest): Promise<TResponse> {
  const token = readCookie("accessToken");
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(payload),
  });
  const body: ApiResponse<TResponse> = await res.json();
  if (!body.success) {
    throw new ApiError(body.error?.code ?? "UNKNOWN", body.error?.message ?? "요청에 실패했습니다.");
  }
  return body.data as TResponse;
}

export async function apiDeleteClient<TResponse>(path: string): Promise<TResponse | null> {
  const token = readCookie("accessToken");
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method: "DELETE",
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  });
  const body: ApiResponse<TResponse> = await res.json();
  if (!body.success) {
    throw new ApiError(body.error?.code ?? "UNKNOWN", body.error?.message ?? "요청에 실패했습니다.");
  }
  return body.data;
}
