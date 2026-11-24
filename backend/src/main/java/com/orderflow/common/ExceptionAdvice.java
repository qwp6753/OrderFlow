package com.orderflow.common;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class ExceptionAdvice {
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return ResponseEntity.status(e.status).body(ApiResponse.error(e.status.name(), e.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> invalid(MethodArgumentNotValidException e) {
    var errors = e.getBindingResult().getFieldErrors();
    String msg = errors.isEmpty() ? "请求参数校验失败" : errors.getFirst().getDefaultMessage();
    return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION", msg));
  }


  @ExceptionHandler({
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalidInput(Exception e) {
    return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION", "请求参数格式或范围不正确"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> conflict(DataIntegrityViolationException e) {
    return ResponseEntity.status(409).body(ApiResponse.error("CONFLICT", "数据冲突，请检查唯一编码或关联记录"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<?> denied(AccessDeniedException e) {
    return ResponseEntity.status(403).body(ApiResponse.error("FORBIDDEN", "无权执行此操作"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    if (e instanceof org.springframework.web.ErrorResponse error) {
      return ResponseEntity.status(error.getStatusCode())
          .body(ApiResponse.error("HTTP_ERROR", "请求路径、方法或参数不正确"));
    }
    log.error("请求处理失败", e);
    return ResponseEntity.internalServerError()
        .body(ApiResponse.error("INTERNAL_ERROR", "服务器处理失败"));
  }
}
