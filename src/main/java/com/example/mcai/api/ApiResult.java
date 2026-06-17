package com.example.mcai.api;

public record ApiResult<T>(T value, String error, boolean success) {
    public static <T> ApiResult<T> ok(T value) { return new ApiResult<>(value, null, true); }
    public static <T> ApiResult<T> err(String message) { return new ApiResult<>(null, message, false); }
}
