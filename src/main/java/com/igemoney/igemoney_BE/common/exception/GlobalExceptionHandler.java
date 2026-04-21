package com.igemoney.igemoney_BE.common.exception;

import com.igemoney.igemoney_BE.common.exception.quiz.QuizAttemptNotFoundException;
import com.igemoney.igemoney_BE.common.exception.quiz.DuplicateQuizQuestionException;
import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizAnswerException;
import com.igemoney.igemoney_BE.common.exception.quiz.InvalidQuizCreateRequestException;
import com.igemoney.igemoney_BE.common.exception.quiz.QuizNotFoundException;
import com.igemoney.igemoney_BE.common.exception.user.AdminAccessDeniedException;
import com.igemoney.igemoney_BE.common.exception.user.DuplicateNicknameException;
import com.igemoney.igemoney_BE.common.exception.user.InvalidJwtTokenException;
import com.igemoney.igemoney_BE.common.exception.user.NoUserIdTokenException;
import com.igemoney.igemoney_BE.common.exception.user.NotRegisteredUserException;
import com.igemoney.igemoney_BE.common.exception.user.UserNotFoundException;
import com.igemoney.igemoney_BE.common.exception.topic.TopicNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
        NotRegisteredUserException.class,
        NoUserIdTokenException.class,
        InvalidJwtTokenException.class,
    })

    public ResponseEntity<ErrorResponse> handle401Errors(RuntimeException e) {
        ErrorResponse errorBody = ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), e.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorBody);
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        TopicNotFoundException.class,
        UserNotFoundException.class,
        QuizNotFoundException.class,
        QuizAttemptNotFoundException.class
    })
    public ResponseEntity<ErrorResponse> handleNotFoundErrors(RuntimeException e) {
        ErrorResponse errorBody = ErrorResponse.of(HttpStatus.NOT_FOUND.value(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorBody);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        ErrorResponse errorBody = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            e.getBindingResult().getAllErrors().get(0).getDefaultMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }

    @ExceptionHandler({
        InvalidQuizAnswerException.class,
        InvalidQuizCreateRequestException.class,
        DuplicateQuizQuestionException.class
    })
    public ResponseEntity<ErrorResponse> handleBadQuizRequest(RuntimeException e) {
        ErrorResponse errorBody = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }

    @ExceptionHandler(AdminAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AdminAccessDeniedException e) {
        ErrorResponse errorBody = ErrorResponse.of(
            HttpStatus.FORBIDDEN.value(),
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorBody);
    }

    @ExceptionHandler(DuplicateNicknameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateNickname(DuplicateNicknameException e) {
        ErrorResponse errorBody = ErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            e.getMessage()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
    }
}
