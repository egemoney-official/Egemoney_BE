package com.igemoney.igemoney_BE.quiz.repository;

import com.igemoney.igemoney_BE.quiz.entity.QuizSubjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizSubjectiveRepository extends JpaRepository<QuizSubjective, Long> {
    List<QuizSubjective> findAllByQuizId(Long quizId);

    Optional<QuizSubjective> findFirstByQuizIdAndIsDisplayAnswerTrue(Long quizId);

    Optional<QuizSubjective> findFirstByQuizIdOrderByIdAsc(Long quizId);
}
