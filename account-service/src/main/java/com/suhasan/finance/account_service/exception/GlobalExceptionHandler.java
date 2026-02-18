// // src/main/java/com/suhasan/finance/account_service/exception/GlobalExceptionHandler.java
// package com.suhasan.finance.account_service.exception;

// import com.suhasan.finance.account_service.dto.ErrorResponse;
// import jakarta.servlet.http.HttpServletRequest;
// import org.springframework.http.*;
// import org.springframework.validation.FieldError;
// import org.springframework.web.bind.MethodArgumentNotValidException;
// import org.springframework.web.bind.annotation.*;

// @RestControllerAdvice
// public class GlobalExceptionHandler {

//   // 1) Handle bean-validation failures
//   @ExceptionHandler(MethodArgumentNotValidException.class)
//   @ResponseStatus(HttpStatus.BAD_REQUEST)
//   public ErrorResponse handleValidation(MethodArgumentNotValidException ex,
//                                         HttpServletRequest req) {
//     // Grab first field error (or you can concatenate them)
//     FieldError fe = ex.getBindingResult().getFieldErrors().get(0);
//     String msg = fe.getField() + ": " + fe.getDefaultMessage();
//     return new ErrorResponse(
//       "Validation Failed",
//       msg,
//       req.getRequestURI(),
//       HttpStatus.BAD_REQUEST.value()
//     );
//   }

//   // 2) Handle “not found” or illegal-arg exceptions
//   @ExceptionHandler(IllegalArgumentException.class)
//   public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex,
//                                                       HttpServletRequest req) {
//     ErrorResponse err = new ErrorResponse(
//       "Not Found",
//       ex.getMessage(),
//       req.getRequestURI(),
//       HttpStatus.NOT_FOUND.value()
//     );
//     return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
//   }

//   // 3) Catch‐all for any uncaught exception
//   @ExceptionHandler(Exception.class)
//   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
//   public ErrorResponse handleAll(Exception ex,
//                                  HttpServletRequest req) {
//     return new ErrorResponse(
//       "Internal Server Error",
//       ex.getMessage(),
//       req.getRequestURI(),
//       HttpStatus.INTERNAL_SERVER_ERROR.value()
//     );
//   }
// }

// package com.suhasan.finance.account_service.exception;

// import com.suhasan.finance.account_service.dto.ErrorResponse;
// import jakarta.servlet.http.HttpServletRequest;
// import org.springframework.http.*;
// import org.springframework.validation.FieldError;
// import org.springframework.web.bind.MethodArgumentNotValidException;
// import org.springframework.web.bind.annotation.*;
// +import org.springframework.http.converter.HttpMessageNotReadableException;

// @RestControllerAdvice
// public class GlobalExceptionHandler {

//   // 0) Malformed JSON → 400 Bad Request
//   @ExceptionHandler(HttpMessageNotReadableException.class)
//   @ResponseStatus(HttpStatus.BAD_REQUEST)
//   public ErrorResponse handleJsonParse(
//       HttpMessageNotReadableException ex,
//       HttpServletRequest req
//   ) {
//     return new ErrorResponse(
//       "Malformed JSON",
//       ex.getMostSpecificCause().getMessage(),
//       req.getRequestURI(),
//       HttpStatus.BAD_REQUEST.value()
//     );
//   }

//   // 1) Handle bean-validation failures
//   @ExceptionHandler(MethodArgumentNotValidException.class)
//   @ResponseStatus(HttpStatus.BAD_REQUEST)
//   public ErrorResponse handleValidation(MethodArgumentNotValidException ex,
//                                         HttpServletRequest req) {
//     FieldError fe = ex.getBindingResult().getFieldErrors().get(0);
//     String msg = fe.getField() + ": " + fe.getDefaultMessage();
//     return new ErrorResponse(
//       "Validation Failed",
//       msg,
//       req.getRequestURI(),
//       HttpStatus.BAD_REQUEST.value()
//     );
//   }

//   // 2) Handle “not found” or illegal-arg exceptions
//   @ExceptionHandler(IllegalArgumentException.class)
//   public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex,
//                                                       HttpServletRequest req) {
//     ErrorResponse err = new ErrorResponse(
//       "Not Found",
//       ex.getMessage(),
//       req.getRequestURI(),
//       HttpStatus.NOT_FOUND.value()
//     );
//     return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
//   }

//   // 3) Catch-all for any uncaught exception
//   @ExceptionHandler(Exception.class)
//   @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
//   public ErrorResponse handleAll(Exception ex,
//                                  HttpServletRequest req) {
//     return new ErrorResponse(
//       "Internal Server Error",
//       ex.getMessage(),
//       req.getRequestURI(),
//       HttpStatus.INTERNAL_SERVER_ERROR.value()
//     );
//   }
// }

package com.suhasan.finance.account_service.exception;

import com.suhasan.finance.account_service.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 0) Handle malformed JSON (missing subtype or syntax errors)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleJsonParse(HttpMessageNotReadableException ex,
                                         HttpServletRequest req) {
        String msg = ex.getMostSpecificCause().getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getMessage();
        }
        return new ErrorResponse(
            "Malformed JSON",
            msg,
            req.getRequestURI(),
            HttpStatus.BAD_REQUEST.value()
        );
    }

    // 1) Handle bean-validation failures
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex,
                                          HttpServletRequest req) {
        FieldError fe = ex.getBindingResult().getFieldErrors().get(0);
        String msg = fe.getField() + ": " + fe.getDefaultMessage();
        return new ErrorResponse(
            "Validation Failed",
            msg,
            req.getRequestURI(),
            HttpStatus.BAD_REQUEST.value()
        );
    }

    // 2) Handle not-found or illegal-arg exceptions
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalArgumentException ex,
                                                        HttpServletRequest req) {
        ErrorResponse err = new ErrorResponse(
            "Not Found",
            ex.getMessage(),
            req.getRequestURI(),
            HttpStatus.NOT_FOUND.value()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest req) {
        ErrorResponse err = new ErrorResponse(
            "Forbidden",
            ex.getMessage(),
            req.getRequestURI(),
            HttpStatus.FORBIDDEN.value()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(err);
    }

    // 3) Catch-all for any uncaught exceptions
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleAll(Exception ex,
                                   HttpServletRequest req) {
        return new ErrorResponse(
            "Internal Server Error",
            ex.getMessage(),
            req.getRequestURI(),
            HttpStatus.INTERNAL_SERVER_ERROR.value()
        );
    }
}
