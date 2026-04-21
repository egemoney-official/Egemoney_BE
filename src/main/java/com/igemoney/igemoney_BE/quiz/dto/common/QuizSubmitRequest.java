package com.igemoney.igemoney_BE.quiz.dto.common;


public record QuizSubmitRequest(
	Long selectedSelectId,
	String subjectiveAnswer
) {
}
