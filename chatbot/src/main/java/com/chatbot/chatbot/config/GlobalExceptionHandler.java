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
 * 非 SSE 接口的统一错误响应体（ErrorResponse）：{timestamp,status,error,message,path}，
 * 前端认 status 分支、读 message 直接展示。
 * <p>
 * 刻意不加 catch-all 的 Exception 处理器：/chat 的错误已在异步线程里用 ChatEvent.error 发给前端，
 * catch-all 会试图往已提交的 text/event-stream 响应里再写一份 JSON。
 * 每个 handler 都显式设 Content-Type，否则内容协商会因为 Accept 是 text/event-stream 变成 406，把真实状态码盖掉。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 会话不存在（404）、方法不允许（405）等：保留状态码，reason 放进 message。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex,
                                                              HttpServletRequest request) {
        int status = ex.getStatusCode().value();
        String reason = ex.getReason();
        String message = (reason == null || reason.isBlank()) ? reasonPhrase(status) : reason;
        return build(status, message, request);
    }

    /** @Valid 校验失败：400 + 具体是哪个字段、为什么。 */
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