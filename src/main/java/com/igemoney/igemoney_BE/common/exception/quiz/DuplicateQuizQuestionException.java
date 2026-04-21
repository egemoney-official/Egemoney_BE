package com.igemoney.igemoney_BE.common.exception.quiz;

public class DuplicateQuizQuestionException extends RuntimeException {

    private static final String DEFAULT_MESSAGE = "이미 같은 토픽에 등록된 문제입니다.";

    public DuplicateQuizQuestionException() {
        super(DEFAULT_MESSAGE);
    }

    public DuplicateQuizQuestionException(String message) {
        super(message);
    }
}
