package com.losgai.sys.common.sys;// GlobalExceptionHandler.java

import com.losgai.sys.enums.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<String> handleNoResourceFound(NoResourceFoundException e) {
        log.error("[资源未找到] {}", e.getMessage());
        return Result.error("资源未找到：" + e.getResourcePath());
    }

    // 处理所有未被捕获的异常（兜底）
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpectedException(Exception ex) {
        Result<Void> errorResult = Result.error("系统内部错误");
        errorResult.setCode(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
        // 可以选择打印日志
        log.error("[全局异常捕获] 未知异常", ex);
        return errorResult;
    }

    // 处理参数校验异常
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return errors;
    }
}