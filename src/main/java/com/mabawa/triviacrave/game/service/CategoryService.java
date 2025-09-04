package com.mabawa.triviacrave.game.service;

import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.game.entity.Question;

public interface CategoryService {
    // Mutation methods - return ApiResponse
    ApiResponse createCategory(CreateCategoryCmd cmd);
    ApiResponse updateCategory(UpdateCategoryCmd cmd);
    ApiResponse deleteCategory(Long categoryId);
    
    // Query methods - return direct types for GraphQL queries
    java.util.List<Category> getCategories(Boolean activeOnly);
    Category getCategory(Long categoryId);
    CategoryStats getCategoryStats(Long categoryId);
    java.util.List<CategoryStats> getAllCategoryStats();
    
    // Domain service methods for other services
    com.mabawa.triviacrave.game.entity.Category getCategoryEntityById(Long categoryId);
    boolean categoryExists(Long categoryId);
    java.util.List<Question> getCategoryActiveQuestions(Long categoryId);
}