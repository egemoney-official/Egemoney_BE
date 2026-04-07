package com.igemoney.igemoney_BE.quiz.repository;

import com.igemoney.igemoney_BE.quiz.entity.QuizSelect;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizSelectRepository extends JpaRepository<QuizSelect, Long> {
    List<QuizSelect> findAllByQuizIdOrderBySelectOrderAsc(Long quizId);

    Optional<QuizSelect> findByIdAndQuizId(Long id, Long quizId);

    Optional<QuizSelect> findFirstByQuizIdAndIsAnswerTrueOrderBySelectOrderAsc(Long quizId);
}
