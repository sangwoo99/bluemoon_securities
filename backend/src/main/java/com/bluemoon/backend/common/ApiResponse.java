package com.bluemoon.backend.common;

public record ApiResponse<T>(boolean success, T data, ApiErrorBody error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static <T> ApiResponse<T> fail(String code, String message) {
        return new ApiResponse<>(false, null, new ApiErrorBody(code, message));
    }

    public record ApiErrorBody(String code, String message) {
    }
}
