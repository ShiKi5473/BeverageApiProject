package tw.niels.beverage_api_project.modules.product.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.product.entity.Category;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long>{

}
