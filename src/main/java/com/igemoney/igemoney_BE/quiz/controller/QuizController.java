package com.igemoney.igemoney_BE.quiz.controller;

import com.igemoney.igemoney_BE.attendance.service.AttendanceService;
import com.igemoney.igemoney_BE.common.annotation.Authenticated;
import com.igemoney.igemoney_BE.quiz.dto.common.BookmarkListResponse;
import com.igemoney.igemoney_BE.quiz.dto.common.QuizResponse;
import com.igemoney.igemoney_BE.quiz.dto.common.QuizReviewResponse;
import com.igemoney.igemoney_BE.quiz.dto.common.QuizSubmitRequest;
import com.igemoney.igemoney_BE.quiz.dto.common.QuizSubmitResponse;
import com.igemoney.igemoney_BE.quiz.service.bookmark.BookmarkService;
import com.igemoney.igemoney_BE.quiz.service.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quiz")
@RequiredArgsConstructor
@Tag(name = "Quiz", description = "Quiz API")
public class QuizController {
    private final QuizService quizService;
    private final AttendanceService attendanceService;
    private final BookmarkService bookmarkService;

    @Authenticated
    @GetMapping("/{quizId}")
    public QuizResponse getQuiz(@PathVariable Long quizId, @RequestAttribute Long userId) {
        return quizService.getQuizInfo(quizId, userId);
    }

    @Authenticated
    @PostMapping("/{id}/submit")
    public QuizSubmitResponse submitQuizResult(@PathVariable Long id, @RequestBody QuizSubmitRequest request,
                                 @RequestAttribute Long userId) {
        QuizSubmitResponse response = quizService.submitQuizResult(id, request, userId);
        attendanceService.incrementTodaySolvedCount(userId);
        return response;
    }

    @Authenticated
    @GetMapping("/wrong")
    public QuizReviewResponse getQuizWrong(@RequestAttribute("userId") Long userId) {
        return quizService.getWrongQuiz(userId);
    }

    @Authenticated
    @GetMapping("/review")
    public QuizReviewResponse getQuizReviews(@RequestAttribute("userId") Long userId) {
        return quizService.getQuizReview(userId);
    }

    @Authenticated
    @PostMapping("/review/{quizId}")
    public QuizSubmitResponse submitQuizReview(@PathVariable Long quizId, @RequestBody QuizSubmitRequest request,
                                 @RequestAttribute Long userId) {
        return quizService.submitReviewQuiz(quizId, request, userId);
    }

    @Authenticated
    @GetMapping("/bookmark")
    @Operation(summary = "북마크 목록 조회")
    public BookmarkListResponse getBookmarkList(@RequestAttribute("userId") Long userId) {
        return bookmarkService.getBookmarkList(userId);
    }

    @Authenticated
    @PostMapping("/bookmark/{quizId}")
    @Operation(summary = "북마크 추가/삭제 토글")
    public void addBookmark(@RequestAttribute("userId") Long userId, @PathVariable Long quizId) {
        bookmarkService.toggleBookmark(userId, quizId);
    }

}
