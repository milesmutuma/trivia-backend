package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.generated.graphql.types.*;

public interface QuestionService {
    // Mutation methods - return ApiResponse
    ApiResponse createQuestion(CreateQuestionCmd cmd);
    ApiResponse updateQuestion(UpdateQuestionCmd cmd);
    ApiResponse deleteQuestion(Long questionId);
    ApiResponse toggleQuestionStatus(Long questionId);
    ApiResponse validateAnswer(Long questionId, String answer);
    
    // Query methods - return direct types for GraphQL queries
    java.util.List<Question> getQuestions(QuestionFilterCmd filter, Integer limit, Integer offset);
    Question getQuestion(Long questionId);
    java.util.List<Question> getQuestionsByCategory(Long categoryId, Difficulty difficulty, Integer limit, Integer offset);
    java.util.List<Question> getRandomQuestions(java.util.List<Long> categoryIds, Difficulty difficulty, int count);
    
    // Domain service methods for other services
    java.util.List<com.mabawa.triviacrave.game.entity.Question> getAllActiveQuestions();
    java.util.List<com.mabawa.triviacrave.game.entity.Question> getRandomActiveQuestions(java.util.List<Long> categoryIds, com.mabawa.triviacrave.generated.graphql.types.Difficulty difficulty, int count);
}