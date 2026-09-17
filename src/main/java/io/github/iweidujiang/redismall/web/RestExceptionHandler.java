package io.github.iweidujiang.redismall.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把业务里抛出的非法参数收成 400，避免堆栈直接回给页面。
 *
 * @author https://github.com/iweidujiang
 */
@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> badRequest(IllegalArgumentException e) {
        return ApiResult.fail(e.getMessage());
    }
}
