package tw.niels.beverage_api_project.modules.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.product.entity.Recipe;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    // 查詢跟「飲品規格」有關的所有配方
    @Query("SELECT r FROM Recipe r WHERE r.variant IS NOT NULL")
    List<Recipe> findAllVariantRecipes();

    // 查詢跟「加料選項」有關的所有配方
    @Query("SELECT r FROM Recipe r WHERE r.option IS NOT NULL")
    List<Recipe> findAllOptionRecipes();
}