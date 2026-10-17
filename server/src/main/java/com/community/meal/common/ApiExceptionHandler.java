package com.community.meal.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
  private final Db db;

  public ApiExceptionHandler(Db db) {
    this.db = db;
  }

  private void auditFailure(String code) {
    try {
      var a =
          org.springframework.security.core.context.SecurityContextHolder.getContext()
              .getAuthentication();
      if (a != null && a.getPrincipal() instanceof com.community.meal.auth.AuthPrincipal p) {
        var attrs =
            org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        String uri =
            attrs instanceof org.springframework.web.context.request.ServletRequestAttributes s
                ? s.getRequest().getRequestURI()
                : "";
        db.exec(
            "INSERT INTO"
                + " audit_log(operator_user_id,action,resource_type,resource_id,success,summary)VALUES(?,'API_FAILURE','API',?,0,?)",
            p.userId(),
            uri.substring(0, Math.min(uri.length(), 80)),
            code);
      }
    } catch (Exception ignored) {
    }
  }

  @ExceptionHandler(ApiException.class)
  ResponseEntity<ApiResponse<Void>> business(ApiException error) {
    auditFailure(error.code());
    return ResponseEntity.status(error.status())
        .body(ApiResponse.error(error.code(), error.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    HttpMessageNotReadableException.class,
    ConstraintViolationException.class,
    HandlerMethodValidationException.class,
    MethodArgumentTypeMismatchException.class,
    java.time.DateTimeException.class,
    IllegalArgumentException.class
  })
  ResponseEntity<ApiResponse<Void>> invalid(Exception ignored) {
    // Do not echo rejected password / health / identity values from validation exceptions.
    return ResponseEntity.badRequest()
        .body(ApiResponse.error("VALIDATION_ERROR", "请求参数不正确，请检查后重试"));
  }

  @ExceptionHandler(AccessDeniedException.class)
  ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException ignored) {
    return business(ApiException.forbidden());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiResponse<Void>> missing(NoResourceFoundException ignored) {
    return business(ApiException.notFound());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiResponse<Void>> method(HttpRequestMethodNotSupportedException ignored) {
    return ResponseEntity.status(405).body(ApiResponse.error("METHOD_NOT_ALLOWED", "该接口不支持此请求方法"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiResponse<Void>> internal(Exception error) {
    log.error("Unhandled request failure ({})", error.getClass().getSimpleName());
    return ResponseEntity.internalServerError()
        .body(ApiResponse.error("INTERNAL_ERROR", "服务暂不可用，请稍后重试"));
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ApiResponse<Void>> tooLarge(
      org.springframework.web.multipart.MaxUploadSizeExceededException ignored) {
    return ResponseEntity.status(413).body(ApiResponse.error("VALIDATION_ERROR", "附件超过上传大小限制"));
  }

  @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
  ResponseEntity<ApiResponse<Void>> conflict(
      org.springframework.dao.DataIntegrityViolationException ignored) {
    return ResponseEntity.status(409).body(ApiResponse.error("CONFLICT", "记录已存在或仍有关联数据，请检查后重试"));
  }
}
