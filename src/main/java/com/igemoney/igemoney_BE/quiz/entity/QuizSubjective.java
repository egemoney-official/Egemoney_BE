package com.igemoney.igemoney_BE.quiz.entity;

import com.igemoney.igemoney_BE.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_subjective",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_quiz_subjective_answer", columnNames = {"quiz_id", "answer_text"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSubjective extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_subjective_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "answer_text", nullable = false, length = 200)
    private String answerText;

    @Column(name = "is_display_answer", nullable = false)
    private Boolean isDisplayAnswer;

    @Builder
    public QuizSubjective(String answerText, Boolean isDisplayAnswer) {
        this.answerText = answerText;
        this.isDisplayAnswer = isDisplayAnswer;
    }

    void assignQuiz(Quiz quiz) {
        this.quiz = quiz;
    }
}
