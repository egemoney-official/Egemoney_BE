package com.igemoney.igemoney_BE.quiz.repository;

import com.igemoney.igemoney_BE.quiz.entity.QuizSubjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizSubjectiveRepository extends JpaRepository<QuizSubjective, Long> {
    List<QuizSubjective> findAllByQuizId(Long quizId);
}
