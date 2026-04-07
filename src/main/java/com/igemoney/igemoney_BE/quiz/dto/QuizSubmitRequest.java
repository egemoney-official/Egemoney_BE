package com.igemoney.igemoney_BE.quiz.dto;


public record QuizSubmitRequest(
	Long selectedSelectId,
	String subjectiveAnswer
) {
}
