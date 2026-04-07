package com.igemoney.igemoney_BE.common.exception.topic;

public class TopicNotFoundException extends RuntimeException {
    public TopicNotFoundException(Long topicId) {
        super("Topic not found with id: " + topicId);
    }
}
