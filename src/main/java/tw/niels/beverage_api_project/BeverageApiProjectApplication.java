package tw.niels.beverage_api_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class BeverageApiProjectApplication {



	public static void main(String[] args) {
        SpringApplication.run(BeverageApiProjectApplication.class, args);
	}

}
