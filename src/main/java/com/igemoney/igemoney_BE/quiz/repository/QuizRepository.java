package com.igemoney.igemoney_BE.quiz.repository;

import com.igemoney.igemoney_BE.quiz.entity.Quiz;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    @EntityGraph(attributePaths = {"topic"})
    Page<Quiz> findAllByTopicId(Long topicId, Pageable pageable);

    @EntityGraph(attributePaths = {"topic"})
    Page<Quiz> findAllByQuestionType(QuestionType questionType, Pageable pageable);

    @EntityGraph(attributePaths = {"topic"})
    Page<Quiz> findAllByTopicIdAndQuestionType(Long topicId, QuestionType questionType, Pageable pageable);

    @EntityGraph(attributePaths = {"topic", "selects", "subjectives"})
    Optional<Quiz> findDetailedById(Long id);

    @EntityGraph(attributePaths = {"topic"})
    List<Quiz> findAllByTopicId(Long topicId);

    @Query("select max(q.questionOrder) from Quiz q where q.topic.id = :topicId")
    Integer findMaxQuestionOrderByTopicId(Long topicId);
}
