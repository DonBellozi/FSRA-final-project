package ru.mephi.fsra.booking.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  public record ErrorDTO(Instant timestamp, int status, String message, String path) {}

  private ResponseEntity<ErrorDTO> error(int status, String message, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(new ErrorDTO(Instant.now(), status, message, request.getRequestURI()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ErrorDTO> status(ResponseStatusException e, HttpServletRequest request) {
    return error(e.getStatusCode().value(), e.getReason(), request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ErrorDTO> conflict(DataIntegrityViolationException e, HttpServletRequest request) {
    return error(409, "Data conflicts with an existing record or reference", request);
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.bind.MissingRequestHeaderException.class
  })
  ResponseEntity<ErrorDTO> invalid(Exception e, HttpServletRequest request) {
    return error(400, "Invalid request data or missing required parameter", request);
  }
}
