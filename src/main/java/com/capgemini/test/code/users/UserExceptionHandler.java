package com.capgemini.test.code.users;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserController.class)
public class UserExceptionHandler {

  public record ApiError(int code, String message) {
  }

  @ExceptionHandler(UserValidationException.class)
  public ResponseEntity<ApiError> validation(UserValidationException exception) {
    return conflict(exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> invalidRequest(MethodArgumentNotValidException exception) {
    String field = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField()).sorted().findFirst().orElse("request");
    return conflict("error validation " + ("name".equals(field) ? "userName" : field));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> duplicate(DataIntegrityViolationException exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConstraintViolationException constraint) {
        String field = switch (String.valueOf(constraint.getConstraintName())) {
          case "users_email_key" -> "email";
          case "users_dni_key" -> "dni";
          case "users_phone_key" -> "phone";
          default -> null;
        };
        if (field != null) {
          return conflict("error validation " + field);
        }
      }
    }
    throw exception;
  }

  private ResponseEntity<ApiError> conflict(String message) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(409, message));
  }
}
