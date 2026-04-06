package com.igemoney.igemoney_BE.quiz.entity;

import com.igemoney.igemoney_BE.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "quiz_select",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_quiz_select_order", columnNames = {"quiz_id", "select_order"})
    }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuizSelect extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_select_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(name = "select_order", nullable = false)
    private Integer selectOrder;

    @Column(name = "select_text", nullable = false, length = 200)
    private String selectText;

    @Column(name = "is_answer", nullable = false)
    private Boolean isAnswer;

    @Builder
    public QuizSelect(Integer selectOrder, String selectText, Boolean isAnswer) {
        this.selectOrder = selectOrder;
        this.selectText = selectText;
        this.isAnswer = isAnswer;
    }

    void assignQuiz(Quiz quiz) {
        this.quiz = quiz;
    }
}
