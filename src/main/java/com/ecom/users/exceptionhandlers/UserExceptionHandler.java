package com.ecom.users.exceptionhandlers;

import com.ecom.common.dtos.ErrorDto;
import com.ecom.users.exceptions.ForbiddenException;
import com.ecom.users.exceptions.InvalidCredentialsException;
import com.ecom.users.exceptions.InvalidTokenException;
import com.ecom.users.exceptions.UserAlreadyExistsException;
import com.ecom.users.exceptions.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class UserExceptionHandler {
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorDto> handleUserAlreadyExists(UserAlreadyExistsException e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler({InvalidCredentialsException.class, InvalidTokenException.class})
    public ResponseEntity<ErrorDto> handleUnauthorized(Exception e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorDto> handleForbidden(ForbiddenException e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorDto> handleUserNotFound(UserNotFoundException e) {
        return new ResponseEntity<>(new ErrorDto(e.getMessage()), HttpStatus.NOT_FOUND);
    }
}
