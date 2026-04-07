package com.igemoney.igemoney_BE.quiz.entity;

import com.igemoney.igemoney_BE.common.entity.BaseEntity;
import com.igemoney.igemoney_BE.quiz.entity.enums.DifficultyLevel;
import com.igemoney.igemoney_BE.quiz.entity.enums.QuestionType;
import com.igemoney.igemoney_BE.topic.entity.QuizTopic;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "quiz")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quiz extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "quiz_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private QuizTopic topic;

    @Column(name = "question_title", length = 200, nullable = false)
    private String questionTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("selectOrder ASC")
    private final List<QuizSelect> selects = new ArrayList<>();

    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private final List<QuizSubjective> subjectives = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty_level", nullable = false)
    private DifficultyLevel difficultyLevel;

    private String explanation;

    @Column(name = "question_order")
    private Integer questionOrder;

    @Column(name = "correct_rate", precision = 5, scale = 2)
    private BigDecimal correctRate;


    @Builder
    public Quiz(QuizTopic topic, String questionTitle, QuestionType questionType, DifficultyLevel difficultyLevel, String explanation, Integer questionOrder) {
        this.topic = topic;
        this.questionTitle = questionTitle;
        this.questionType = questionType;
        this.difficultyLevel = difficultyLevel;
        this.explanation = explanation;
        this.questionOrder = questionOrder;
        this.correctRate = BigDecimal.ZERO;
    }
    public void updateCorrectRate(BigDecimal newCorrectRate) {
        this.correctRate = newCorrectRate;
    }

    public void addSelect(QuizSelect select) {
        selects.add(select);
        select.assignQuiz(this);
    }

    public void addSubjective(QuizSubjective subjective) {
        subjectives.add(subjective);
        subjective.assignQuiz(this);
    }
}
