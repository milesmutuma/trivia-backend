package com.mabawa.triviacrave.game.resources;

import com.mabawa.triviacrave.generated.graphql.types.*;
import com.mabawa.triviacrave.game.service.CategoryService;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.DgsQuery;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@DgsComponent
@RequiredArgsConstructor
public class CategoryDataFetcher {
    private final CategoryService categoryService;

    // Category Queries - Public access for reading categories
    @DgsQuery
    @PreAuthorize("permitAll()")
    public List<Category> getCategories(@InputArgument Boolean activeOnly) {
        return categoryService.getCategories(activeOnly);
    }

    @DgsQuery
    @PreAuthorize("permitAll()")
    public Category getCategory(@InputArgument Long id) {
        return categoryService.getCategory(id);
    }

    @DgsQuery
    @PreAuthorize("permitAll()")
    public CategoryStats getCategoryStats(@InputArgument Long categoryId) {
        return categoryService.getCategoryStats(categoryId);
    }

    @DgsQuery
    @PreAuthorize("permitAll()")
    public List<CategoryStats> getAllCategoryStats() {
        return categoryService.getAllCategoryStats();
    }

    // Category Mutations - Admin operations
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse createCategory(@InputArgument CreateCategoryCmd command) {
        return categoryService.createCategory(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse updateCategory(@InputArgument UpdateCategoryCmd command) {
        return categoryService.updateCategory(command);
    }

    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse deleteCategory(@InputArgument Long id) {
        return categoryService.deleteCategory(id);
    }

}