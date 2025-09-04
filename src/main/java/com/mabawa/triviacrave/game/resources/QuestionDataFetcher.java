package com.mabawa.triviacrave.game.resources;

import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.game.service.QuestionService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class QuestionDataFetcher {
    private final QuestionService questionService;

    // Question Queries - Public access for reading questions
    @DgsQuery
    @PreAuthorize("permitAll()")
    public List<Question> getQuestions(@InputArgument QuestionFilterCmd filter, @InputArgument Integer limit, @InputArgument Integer offset) {
        return questionService.getQuestions(filter, limit, offset);
    }

    @DgsQuery
    @PreAuthorize("permitAll()")
    public Question getQuestion(@InputArgument Long id) {
        return questionService.getQuestion(id);
    }

    @DgsQuery
    @PreAuthorize("permitAll()")
    public List<Question> getQuestionsByCategory(@InputArgument Long categoryId, @InputArgument Difficulty difficulty, @InputArgument Integer limit, @InputArgument Integer offset) {
        return questionService.getQuestionsByCategory(categoryId, difficulty, limit, offset);
    }

    @DgsQuery
    @PreAuthorize("hasRole('USER')")
    public List<Question> getRandomQuestions(@InputArgument List<Long> categoryIds, @InputArgument Difficulty difficulty, @InputArgument Integer count) {
        return questionService.getRandomQuestions(categoryIds, difficulty, count);
    }

    // Question Mutations - Admin operations
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse createQuestion(@InputArgument CreateQuestionCmd command) {
        return questionService.createQuestion(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse updateQuestion(@InputArgument UpdateQuestionCmd command) {
        return questionService.updateQuestion(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse deleteQuestion(@InputArgument Long id) {
        return questionService.deleteQuestion(id);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse toggleQuestionStatus(@InputArgument Long id) {
        return questionService.toggleQuestionStatus(id);
    }
}