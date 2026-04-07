package com.igemoney.igemoney_BE.common.exception.quiz;

public class QuizAttemptNotFoundException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "존재하지 않는 퀴즈 시도 기록입니다.";

    public QuizAttemptNotFoundException() {
        super(DEFAULT_MESSAGE);
    }

    public QuizAttemptNotFoundException(String message) {
        super(message);
    }
}
