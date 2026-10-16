package tw.niels.beverage_api_project.modules.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.product.entity.RecipeEntity;

import java.util.Collection;
import java.util.List;

@Repository
public interface RecipeJpaRepository extends JpaRepository<RecipeEntity, Long> {
    // 查詢跟「飲品規格」有關的所有配方
    @Query("SELECT r FROM RecipeEntity r WHERE r.variant IS NOT NULL")
    List<RecipeEntity> findAllVariantRecipeEntitys();

    // 查詢跟「加料選項」有關的所有配方
    @Query("SELECT r FROM RecipeEntity r WHERE r.option IS NOT NULL")
    List<RecipeEntity> findAllOptionRecipeEntitys();

    /**
     * 批次查詢指定規格 ID 清單的所有配方（含原料關聯）
     * 供訂單完成後庫存扣減使用，避免 N+1 問題
     */
    @Query("SELECT r FROM RecipeEntity r JOIN FETCH r.inventoryItem WHERE r.variant.id IN :variantIds")
    List<RecipeEntity> findByVariantIdIn(@Param("variantIds") Collection<Long> variantIds);

    /**
     * 批次查詢指定選項 ID 清單的所有配方（含原料關聯）
     * 供訂單完成後庫存扣減使用，避免 N+1 問題
     */
    @Query("SELECT r FROM RecipeEntity r JOIN FETCH r.inventoryItem WHERE r.option.id IN :optionIds")
    List<RecipeEntity> findByOptionIdIn(@Param("optionIds") Collection<Long> optionIds);
}