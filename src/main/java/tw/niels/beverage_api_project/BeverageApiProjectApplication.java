package tw.niels.beverage_api_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@EnableScheduling
@EnableRetry
@EnableAsync
@EnableJpaRepositories(basePackages = "tw.niels.beverage_api_project.modules")
@EnableMongoRepositories(basePackages = "tw.niels.beverage_api_project.modules.audit.repository")
@SpringBootApplication
public class BeverageApiProjectApplication {



	public static void main(String[] args) {
        SpringApplication.run(BeverageApiProjectApplication.class, args);
	}

}
