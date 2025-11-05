package tw.niels.beverage_api_project;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class BeverageApiProjectApplication {



	public static void main(String[] args) {
 String plainPassword = "superadmin123";
         BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
         String hashedPassword = encoder.encode(plainPassword);
         System.out.println("====================================================================");
         System.out.println("您的密碼雜湊值為: " + hashedPassword);
         System.out.println("====================================================================");
        SpringApplication.run(BeverageApiProjectApplication.class, args);
	}

}
