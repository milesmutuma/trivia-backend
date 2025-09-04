package com.mabawa.triviacrave.game.service.impl;

import com.mabawa.triviacrave.common.utils.IDGenerator;
import com.mabawa.triviacrave.game.entity.Category;
import com.mabawa.triviacrave.game.entity.Question;
import com.mabawa.triviacrave.game.repository.CategoryRepository;
import com.mabawa.triviacrave.game.repository.QuestionRepository;
import com.mabawa.triviacrave.game.service.QuestionService;
import com.mabawa.triviacrave.generated.graphql.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {
    private final QuestionRepository questionRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public ApiResponse createQuestion(CreateQuestionCmd cmd) {
        try {
            // Validate input
            validateCreateQuestionInput(cmd);

            // Check if category exists
            Category category = categoryRepository.findOne(cmd.getCategoryId());
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }

            Long id = IDGenerator.generateId();
            Question question = Question.builder()
                    .id(id)
                    .questionText(cmd.getQuestionText().trim())
                    .correctAnswer(cmd.getCorrectAnswer().trim())
                    .difficulty(Question.Difficulty.valueOf(cmd.getDifficulty().name()))
                    .explanation(cmd.getExplanation() != null ? cmd.getExplanation().trim() : null)
                    .isActive(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .category(category)
                    .build();

            // Set multiple choice options if provided
            List<String> wrongAnswers = cmd.getWrongAnswers();
            if (wrongAnswers != null && wrongAnswers.size() >= 3) {
                question.setOptionA(cmd.getCorrectAnswer().trim());
                question.setOptionB(wrongAnswers.get(0).trim());
                question.setOptionC(wrongAnswers.get(1).trim());
                question.setOptionD(wrongAnswers.get(2).trim());
            }

            questionRepository.save(question);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Question created successfully")
                    .data(mapToGraphQLQuestion(question))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for create question: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error creating question: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to create question")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    @Override
    @Transactional
    public ApiResponse updateQuestion(UpdateQuestionCmd cmd) {
        try {
            if (cmd.getId() <= 0) {
                throw new IllegalArgumentException("Question ID is required");
            }

            Question question = questionRepository.findOne(cmd.getId());
            if (question == null) {
                throw new IllegalArgumentException("Question not found");
            }

            // Update category if provided
            if (cmd.getCategoryId() != null && !cmd.getCategoryId().equals(question.getCategory().getId())) {
                Category category = categoryRepository.findOne(cmd.getCategoryId());
                if (category == null) {
                    throw new IllegalArgumentException("Category not found");
                }
                question.setCategory(category);
            }

            if (cmd.getQuestionText() != null) {
                question.setQuestionText(cmd.getQuestionText().trim());
            }

            if (cmd.getCorrectAnswer() != null) {
                question.setCorrectAnswer(cmd.getCorrectAnswer().trim());
            }

            if (cmd.getDifficulty() != null) {
                question.setDifficulty(Question.Difficulty.valueOf(cmd.getDifficulty().name()));
            }

            if (cmd.getExplanation() != null) {
                question.setExplanation(cmd.getExplanation().trim());
            }

            if (cmd.getIsActive() != null) {
                question.setActive(cmd.getIsActive());
            }

            // Update multiple choice options if provided
            List<String> wrongAnswers = cmd.getWrongAnswers();
            if (wrongAnswers != null && wrongAnswers.size() >= 3) {
                question.setOptionA(question.getCorrectAnswer());
                question.setOptionB(wrongAnswers.get(0).trim());
                question.setOptionC(wrongAnswers.get(1).trim());
                question.setOptionD(wrongAnswers.get(2).trim());
            }

            question.setUpdatedAt(LocalDateTime.now());
            questionRepository.save(question);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Question updated successfully")
                    .data(mapToGraphQLQuestion(question))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for update question: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error updating question: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to update question")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    @Override
    @Transactional
    public ApiResponse deleteQuestion(Long questionId) {
        try {
            if (questionId == null) {
                throw new IllegalArgumentException("Question ID is required");
            }

            Question question = questionRepository.findOne(questionId);
            if (question == null) {
                throw new IllegalArgumentException("Question not found");
            }

            // Soft delete by marking as inactive
            question.setActive(false);
            question.setUpdatedAt(LocalDateTime.now());
            questionRepository.save(question);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Question deleted successfully")
                    .data(Empty.newBuilder().ok(true).build())
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for delete question: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error deleting question: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to delete question")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    @Override
    @Transactional
    public ApiResponse toggleQuestionStatus(Long questionId) {
        try {
            if (questionId == null) {
                throw new IllegalArgumentException("Question ID is required");
            }

            Question question = questionRepository.findOne(questionId);
            if (question == null) {
                throw new IllegalArgumentException("Question not found");
            }

            question.setActive(!question.isActive());
            question.setUpdatedAt(LocalDateTime.now());
            questionRepository.save(question);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Question status toggled successfully")
                    .data(mapToGraphQLQuestion(question))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for toggle question status: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error toggling question status: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to toggle question status")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }





    @Override
    public ApiResponse validateAnswer(Long questionId, String answer) {
        try {
            if (questionId == null) {
                throw new IllegalArgumentException("Question ID is required");
            }

            if (answer == null || answer.trim().isEmpty()) {
                throw new IllegalArgumentException("Answer is required");
            }

            Question question = questionRepository.findOne(questionId);
            if (question == null) {
                throw new IllegalArgumentException("Question not found");
            }

            boolean isCorrect = question.isCorrectAnswer(answer);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Answer validated successfully")
                    .data(com.mabawa.triviacrave.generated.graphql.types.AnswerValidation.newBuilder()
                            .isCorrect(isCorrect)
                            .correctAnswer(question.getCorrectAnswer())
                            .explanation(question.getExplanation())
                            .points(isCorrect ? question.getPointValue() : 0)
                            .build())
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for validate answer: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("Error validating answer: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to validate answer")
                    .data(null)
                    .build();
        }
    }

    private void validateCreateQuestionInput(CreateQuestionCmd cmd) {
        if (cmd.getCategoryId() <= 0) {
            throw new IllegalArgumentException("Category ID is required");
        }
        if (cmd.getQuestionText() == null || cmd.getQuestionText().trim().isEmpty()) {
            throw new IllegalArgumentException("Question text is required");
        }
        if (cmd.getCorrectAnswer() == null || cmd.getCorrectAnswer().trim().isEmpty()) {
            throw new IllegalArgumentException("Correct answer is required");
        }
        if (cmd.getDifficulty() == null) {
            throw new IllegalArgumentException("Difficulty is required");
        }
        if (cmd.getWrongAnswers() == null || cmd.getWrongAnswers().size() < 3) {
            throw new IllegalArgumentException("At least 3 wrong answers are required for multiple choice questions");
        }
        if (cmd.getPoints() <= 0) {
            throw new IllegalArgumentException("Points must be positive");
        }
    }

    private com.mabawa.triviacrave.generated.graphql.types.Question mapToGraphQLQuestion(Question question) {
        List<String> wrongAnswers = new ArrayList<>();
        List<String> allAnswers = new ArrayList<>();
        
        if (question.hasMultipleChoiceOptions()) {
            wrongAnswers.add(question.getOptionB());
            wrongAnswers.add(question.getOptionC());
            wrongAnswers.add(question.getOptionD());
            
            allAnswers.add(question.getOptionA());
            allAnswers.add(question.getOptionB());
            allAnswers.add(question.getOptionC());
            allAnswers.add(question.getOptionD());
        }

        return com.mabawa.triviacrave.generated.graphql.types.Question.newBuilder()
                .id(question.getId())
                .categoryId(question.getCategory().getId())
                .questionText(question.getQuestionText())
                .correctAnswer(question.getCorrectAnswer())
                .wrongAnswers(wrongAnswers)
                .allAnswers(allAnswers)
                .difficulty(Difficulty.valueOf(question.getDifficulty().name()))
                .explanation(question.getExplanation())
                .points(question.getPointValue())
                .isActive(question.isActive())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }
    // Interface methods that return direct types for GraphQL queries
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Question> getQuestions(QuestionFilterCmd filter, Integer limit, Integer offset) {
        List<Question> questions = questionRepository.findAll();
        
        // Apply filters
        if (filter != null) {
            if (filter.getCategoryId() != null) {
                questions = questions.stream()
                        .filter(q -> q.getCategory().getId().equals(filter.getCategoryId()))
                        .toList();
            }
            
            if (filter.getDifficulty() != null) {
                com.mabawa.triviacrave.game.entity.Question.Difficulty entityDifficulty = mapToEntityDifficulty(filter.getDifficulty());
                questions = questions.stream()
                        .filter(q -> q.getDifficulty() == entityDifficulty)
                        .toList();
            }
            
            if (filter.getIsActive() != null) {
                questions = questions.stream()
                        .filter(q -> q.isActive() == filter.getIsActive())
                        .toList();
            }
            
            if (filter.getSearchTerm() != null && !filter.getSearchTerm().trim().isEmpty()) {
                String searchTerm = filter.getSearchTerm().toLowerCase().trim();
                questions = questions.stream()
                        .filter(q -> q.getQuestionText().toLowerCase().contains(searchTerm))
                        .toList();
            }
        }
        
        // Apply pagination
        if (offset != null && offset > 0) {
            questions = questions.stream()
                    .skip(offset)
                    .toList();
        }
        
        if (limit != null && limit > 0) {
            questions = questions.stream()
                    .limit(limit)
                    .toList();
        }
        
        return questions.stream()
                .map(this::mapToGraphQLQuestion)
                .toList();
    }
    
    @Override
    public com.mabawa.triviacrave.generated.graphql.types.Question getQuestion(Long questionId) {
        if (questionId == null || questionId <= 0) {
            throw new RuntimeException("Question ID is required");
        }
        
        Question question = questionRepository.findOne(questionId);
        if (question == null) {
            return null; // GraphQL can handle null returns for nullable fields
        }
        
        return mapToGraphQLQuestion(question);
    }
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Question> getQuestionsByCategory(Long categoryId, Difficulty difficulty, Integer limit, Integer offset) {
        if (categoryId == null || categoryId <= 0) {
            throw new RuntimeException("Category ID is required");
        }
        
        Category category = categoryRepository.findOne(categoryId);
        if (category == null) {
            throw new RuntimeException("Category not found");
        }
        
        com.mabawa.triviacrave.game.entity.Question.Difficulty entityDifficulty = mapToEntityDifficulty(difficulty);
        List<Question> questions = category.getQuestions().stream()
                .filter(q -> difficulty == null || q.getDifficulty() == entityDifficulty)
                .toList();
                
        // Apply pagination
        if (offset != null && offset > 0) {
            questions = questions.stream()
                    .skip(offset)
                    .toList();
        }
        
        if (limit != null && limit > 0) {
            questions = questions.stream()
                    .limit(limit)
                    .toList();
        }
        
        return questions.stream()
                .map(this::mapToGraphQLQuestion)
                .toList();
    }
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Question> getRandomQuestions(java.util.List<Long> categoryIds, Difficulty difficulty, int count) {
        if (count <= 0) {
            count = 10; // Default count
        }
        
        List<Question> allQuestions = new ArrayList<>();
        
        if (categoryIds != null && !categoryIds.isEmpty()) {
            for (Long categoryId : categoryIds) {
                Category category = categoryRepository.findOne(categoryId);
                if (category != null) {
                    com.mabawa.triviacrave.game.entity.Question.Difficulty entityDifficulty = mapToEntityDifficulty(difficulty);
                    allQuestions.addAll(category.getQuestions().stream()
                            .filter(Question::isActive)
                            .filter(q -> difficulty == null || q.getDifficulty() == entityDifficulty)
                            .toList());
                }
            }
        } else {
            com.mabawa.triviacrave.game.entity.Question.Difficulty entityDifficulty = mapToEntityDifficulty(difficulty);
            allQuestions = questionRepository.findActiveQuestions().stream()
                    .filter(q -> difficulty == null || q.getDifficulty() == entityDifficulty)
                    .toList();
        }
        
        // Shuffle and limit
        Collections.shuffle(allQuestions);
        
        return allQuestions.stream()
                .limit(count)
                .map(this::mapToGraphQLQuestion)
                .toList();
    }

    // Helper method to convert GraphQL Difficulty to Entity Difficulty
    private com.mabawa.triviacrave.game.entity.Question.Difficulty mapToEntityDifficulty(com.mabawa.triviacrave.generated.graphql.types.Difficulty graphqlDifficulty) {
        if (graphqlDifficulty == null) {
            return null;
        }
        return com.mabawa.triviacrave.game.entity.Question.Difficulty.valueOf(graphqlDifficulty.name());
    }

    // Domain service methods for other services
    @Override
    public java.util.List<com.mabawa.triviacrave.game.entity.Question> getAllActiveQuestions() {
        return questionRepository.findActiveQuestions();
    }

    @Override
    public java.util.List<com.mabawa.triviacrave.game.entity.Question> getRandomActiveQuestions(java.util.List<Long> categoryIds, com.mabawa.triviacrave.generated.graphql.types.Difficulty difficulty, int count) {
        if (count <= 0) {
            count = 10;
        }
        
        List<com.mabawa.triviacrave.game.entity.Question> allQuestions = new ArrayList<>();
        
        if (categoryIds != null && !categoryIds.isEmpty()) {
            for (Long categoryId : categoryIds) {
                Category category = categoryRepository.findOne(categoryId);
                if (category != null) {
                    com.mabawa.triviacrave.game.entity.Question.Difficulty entityDifficulty = mapToEntityDifficulty(difficulty);
                    allQuestions.addAll(category.getQuestions().stream()
                            .filter(com.mabawa.triviacrave.game.entity.Question::isActive)
                            .filter(q -> difficulty == null || q.getDifficulty() == entityDifficulty)
                            .toList());
                }
            }
        } else {
            com.mabawa.triviacrave.game.entity.Question.Difficulty entityDifficulty = mapToEntityDifficulty(difficulty);
            allQuestions = questionRepository.findActiveQuestions().stream()
                    .filter(q -> difficulty == null || q.getDifficulty() == entityDifficulty)
                    .toList();
        }
        
        Collections.shuffle(allQuestions);
        
        return allQuestions.stream()
                .limit(count)
                .toList();
    }

}