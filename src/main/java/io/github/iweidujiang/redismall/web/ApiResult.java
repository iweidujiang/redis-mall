package io.github.iweidujiang.redismall.web;

public record ApiResult<T>(boolean ok, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "ok", data);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(false, message, null);
    }
}
