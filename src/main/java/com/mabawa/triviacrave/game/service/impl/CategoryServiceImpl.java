package com.mabawa.triviacrave.game.service.impl;

import com.mabawa.triviacrave.common.utils.IDGenerator;
import com.mabawa.triviacrave.game.entity.Category;
import com.mabawa.triviacrave.game.entity.Question;
import com.mabawa.triviacrave.game.repository.CategoryRepository;
import com.mabawa.triviacrave.game.service.CategoryService;
import com.mabawa.triviacrave.generated.graphql.types.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public ApiResponse createCategory(CreateCategoryCmd cmd) {
        try {
            // Validate input
            if (cmd.getName() == null || cmd.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("Category name is required");
            }

            // Check if category already exists
            if (categoryRepository.existsByName(cmd.getName())) {
                throw new IllegalArgumentException("Category with this name already exists");
            }

            Long id = IDGenerator.generateId();
            Category category = Category.builder()
                    .id(id)
                    .name(cmd.getName().trim())
                    .description(cmd.getDescription() != null ? cmd.getDescription().trim() : null)
                    .isActive(cmd.getIsActive())
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            categoryRepository.save(category);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Category created successfully")
                    .data(mapToGraphQLCategory(category))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for create category: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error creating category: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to create category")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    @Override
    @Transactional
    public ApiResponse updateCategory(UpdateCategoryCmd cmd) {
        try {
            if (cmd.getId() <= 0) {
                throw new IllegalArgumentException("Category ID is required");
            }

            Category category = categoryRepository.findOne(cmd.getId());
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }

            // Check if new name conflicts with existing category
            if (cmd.getName() != null && !cmd.getName().trim().equals(category.getName())) {
                if (categoryRepository.existsByName(cmd.getName().trim())) {
                    throw new IllegalArgumentException("Category with this name already exists");
                }
                category.setName(cmd.getName().trim());
            }

            if (cmd.getDescription() != null) {
                category.setDescription(cmd.getDescription().trim());
            }

            if (cmd.getIsActive() != null) {
                category.setActive(cmd.getIsActive());
            }

            category.setUpdatedAt(LocalDateTime.now());
            categoryRepository.save(category);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Category updated successfully")
                    .data(mapToGraphQLCategory(category))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for update category: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error updating category: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to update category")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    @Override
    @Transactional
    public ApiResponse deleteCategory(Long categoryId) {
        try {
            if (categoryId == null) {
                throw new IllegalArgumentException("Category ID is required");
            }

            Category category = categoryRepository.findOne(categoryId);
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }

            // Check if category has questions
            if (category.getActiveQuestionCount() > 0) {
                throw new IllegalArgumentException("Cannot delete category with active questions");
            }

            categoryRepository.delete(category);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Category deleted successfully")
                    .data(Empty.newBuilder().ok(true).build())
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for delete category: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        } catch (Exception e) {
            log.error("Error deleting category: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to delete category")
                    .data(Empty.newBuilder().ok(false).build())
                    .build();
        }
    }

    public ApiResponse getCategoriesApiResponse(boolean activeOnly) {
        try {
            List<Category> categories = categoryRepository.findAllOrderedByName();
            
            if (activeOnly) {
                categories = categories.stream()
                        .filter(Category::isActive)
                        .toList();
            }

            List<com.mabawa.triviacrave.generated.graphql.types.Category> graphqlCategories = 
                    categories.stream()
                            .map(this::mapToGraphQLCategory)
                            .toList();

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Categories retrieved successfully")
                    .data(CategoryList.newBuilder().categories(graphqlCategories).build())
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving categories: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve categories")
                    .data(CategoryList.newBuilder().categories(List.of()).build())
                    .build();
        }
    }

    public ApiResponse getCategoryApiResponse(Long categoryId) {
        try {
            if (categoryId == null) {
                throw new IllegalArgumentException("Category ID is required");
            }

            Category category = categoryRepository.findOne(categoryId);
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Category retrieved successfully")
                    .data(mapToGraphQLCategory(category))
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for get category: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("Error retrieving category: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve category")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getCategoryStatsApiResponse(Long categoryId) {
        try {
            if (categoryId == null) {
                throw new IllegalArgumentException("Category ID is required");
            }

            Category category = categoryRepository.findOne(categoryId);
            if (category == null) {
                throw new IllegalArgumentException("Category not found");
            }

            CategoryStats stats = buildCategoryStats(category);

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("Category statistics retrieved successfully")
                    .data(stats)
                    .build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid input for get category stats: {}", e.getMessage());
            return ApiResponse.newBuilder()
                    .status(400)
                    .message(e.getMessage())
                    .data(null)
                    .build();
        } catch (Exception e) {
            log.error("Error retrieving category stats: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve category statistics")
                    .data(null)
                    .build();
        }
    }

    public ApiResponse getAllCategoryStatsApiResponse() {
        try {
            List<Category> categories = categoryRepository.findAllOrderedByName();
            
            List<CategoryStats> allStats = categories.stream()
                    .map(this::buildCategoryStats)
                    .toList();

            return ApiResponse.newBuilder()
                    .status(200)
                    .message("All category statistics retrieved successfully")
                    .data(CategoryStatsList.newBuilder().categoryStats(allStats).build())
                    .build();

        } catch (Exception e) {
            log.error("Error retrieving all category stats: {}", e.getMessage(), e);
            return ApiResponse.newBuilder()
                    .status(500)
                    .message("Failed to retrieve category statistics")
                    .data(CategoryStatsList.newBuilder().categoryStats(List.of()).build())
                    .build();
        }
    }

    private com.mabawa.triviacrave.generated.graphql.types.Category mapToGraphQLCategory(Category category) {
        return com.mabawa.triviacrave.generated.graphql.types.Category.newBuilder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .isActive(category.isActive())
                .questionCount(category.getActiveQuestionCount())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }

    private CategoryStats buildCategoryStats(Category category) {
        List<Question> questions = category.getQuestions();
        
        Map<Question.Difficulty, Long> difficultyCount = questions.stream()
                .filter(Question::isActive)
                .collect(java.util.stream.Collectors.groupingBy(Question::getDifficulty, java.util.stream.Collectors.counting()));

        List<DifficultyCount> questionsPerDifficulty = difficultyCount.entrySet().stream()
                .map(entry -> DifficultyCount.newBuilder()
                        .difficulty(Difficulty.valueOf(entry.getKey().name()))
                        .count(entry.getValue().intValue())
                        .build())
                .toList();

        double averageDifficulty = questions.stream()
                .filter(Question::isActive)
                .mapToDouble(q -> q.getDifficulty().ordinal() + 1)
                .average()
                .orElse(0.0);

        return CategoryStats.newBuilder()
                .categoryId(category.getId())
                .category(mapToGraphQLCategory(category))
                .totalQuestions(category.getQuestionCount())
                .activeQuestions(category.getActiveQuestionCount())
                .averageDifficulty((float) averageDifficulty)
                .questionsPerDifficulty(questionsPerDifficulty)
                .build();
    }

    // Interface methods that return direct types for GraphQL queries
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.Category> getCategories(Boolean activeOnly) {
        List<Category> categories;
        if (activeOnly != null && activeOnly) {
            categories = categoryRepository.findActiveCategories();
        } else {
            categories = categoryRepository.findAll();
        }
        
        return categories.stream()
                .map(this::mapToGraphQLCategory)
                .toList();
    }
    
    @Override
    public com.mabawa.triviacrave.generated.graphql.types.Category getCategory(Long categoryId) {
        if (categoryId <= 0) {
            throw new RuntimeException("Category ID is required");
        }
        
        Category category = categoryRepository.findOne(categoryId);
        if (category == null) {
            return null; // GraphQL can handle null returns for nullable fields
        }
        
        return mapToGraphQLCategory(category);
    }
    
    @Override
    public com.mabawa.triviacrave.generated.graphql.types.CategoryStats getCategoryStats(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new RuntimeException("Category ID is required");
        }
        
        Category category = categoryRepository.findOne(categoryId);
        if (category == null) {
            throw new RuntimeException("Category not found");
        }
        
        return buildCategoryStats(category);
    }
    
    @Override
    public java.util.List<com.mabawa.triviacrave.generated.graphql.types.CategoryStats> getAllCategoryStats() {
        List<Category> categories = categoryRepository.findAll();
        return categories.stream()
                .map(this::buildCategoryStats)
                .toList();
    }
    
    // Domain service methods for other services
    @Override
    public Category getCategoryEntityById(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository.findOne(categoryId);
    }

    @Override
    public boolean categoryExists(Long categoryId) {
        if (categoryId == null) {
            return false;
        }
        return categoryRepository.findOne(categoryId) != null;
    }

    @Override
    public java.util.List<com.mabawa.triviacrave.game.entity.Question> getCategoryActiveQuestions(Long categoryId) {
        if (categoryId == null) {
            return List.of();
        }
        
        Category category = categoryRepository.findOne(categoryId);
        if (category == null) {
            return List.of();
        }
        
        return category.getQuestions().stream()
                .filter(com.mabawa.triviacrave.game.entity.Question::isActive)
                .toList();
    }
    
}