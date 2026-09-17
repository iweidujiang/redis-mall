package io.github.iweidujiang.redismall.web;

/**
 * 接口统一返回体。页面和 curl 都认这套字段。
 *
 * @param ok      是否成功
 * @param message 说明
 * @param data    业务数据
 * @param <T>     数据类型
 * @author https://github.com/iweidujiang
 */
public record ApiResult<T>(boolean ok, String message, T data) {

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "ok", data);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(false, message, null);
    }
}
