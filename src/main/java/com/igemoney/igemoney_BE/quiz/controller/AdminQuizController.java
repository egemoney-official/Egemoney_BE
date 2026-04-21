package com.igemoney.igemoney_BE.quiz.controller;

import com.igemoney.igemoney_BE.quiz.dto.AdminQuizListResponse;
import com.igemoney.igemoney_BE.quiz.dto.QuizCreateRequest;
import com.igemoney.igemoney_BE.quiz.dto.QuizResponse;
import com.igemoney.igemoney_BE.quiz.service.QuizAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/quizzes")
@RequiredArgsConstructor
@Tag(name = "Admin Quiz", description = "관리자 퀴즈 관리 API")
public class AdminQuizController {

    private final QuizAdminService quizAdminService;

    @PostMapping
    @Operation(summary = "퀴즈 생성")
    public QuizResponse createQuiz(@RequestBody QuizCreateRequest request) {
        return quizAdminService.createQuiz(request);
    }

    @GetMapping
    @Operation(summary = "퀴즈 목록 조회")
    public AdminQuizListResponse getQuizzes(
        @RequestParam(required = false) Long topicId,
        @RequestParam(required = false) String questionType,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return quizAdminService.getQuizzes(topicId, questionType, page, size);
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "퀴즈 상세 조회")
    public QuizResponse getQuiz(@PathVariable Long quizId) {
        return quizAdminService.getQuiz(quizId);
    }

    @DeleteMapping("/{quizId}")
    @Operation(summary = "퀴즈 삭제")
    public void deleteQuiz(@PathVariable Long quizId) {
        quizAdminService.deleteQuiz(quizId);
    }
}
