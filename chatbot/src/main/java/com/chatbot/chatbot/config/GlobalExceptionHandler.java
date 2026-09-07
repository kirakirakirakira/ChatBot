package com.chatbot.chatbot.config;

import com.chatbot.chatbot.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * 统一非 SSE 接口的错误响应体（ErrorResponse）。
 * <p>
 * 之前的问题：Spring Boot 默认 server.error.include-message=never，
 * 前端遇到 404 / 400 只能拿到 {"status":404,"error":"Not Found"}，
 * 完全看不到「会话不存在: 3」这类人话文案。现在统一成
 * {timestamp,status,error,message,path}，前端读 message 直接展示。
 * <p>
 * 这里刻意不加 catch-all 的 Exception 处理器：SSE(/chat) 的错误是在异步线程里
 * 通过 ChatEvent.error 事件告诉前端的，catch-all 会试图往已经提交的
 * text/event-stream 响应里再写一份 JSON。其余异常仍然走 Spring 默认 /error，
 * 字段结构与 ErrorResponse 一致。
 * <p>
 * 每个 handler 都显式设了 Content-Type：chat 接口的 Accept 是 text/event-stream，
 * 不显式指定会因为内容协商失败变成 406，把真正的 400 盖掉。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 会话不存在（404）、方法不允许（405）等：保留原始状态码，reason 放进 message。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                              HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String reason = ex.getReason();
        String message = (reason == null || reason.isBlank()) ? reasonPhrase(status) : reason;
        return build(status, message, request);
    }

    /** @Valid 校验失败（比如 message 为空）：400 + 具体是哪个字段、为什么。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::formatFieldError)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST.value(), detail.isBlank() ? "请求参数校验失败" : detail, request);
    }

    /** 请求体不是合法 JSON。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST.value(), "请求体不是合法的 JSON", request);
    }

    private static ResponseEntity<ErrorResponse> build(int status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorResponse.of(status, reasonPhrase(status), message, request.getRequestURI()));
    }

    private static String formatFieldError(FieldError error) {
        String message = error.getDefaultMessage();
        return (message == null || message.isBlank())
                ? error.getField() + " 不合法"
                : error.getField() + ": " + message;
    }

    private static String reasonPhrase(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.getReasonPhrase() : "Error";
    }
}