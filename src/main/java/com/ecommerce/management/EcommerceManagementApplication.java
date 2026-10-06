package com.ecommerce.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcommerceManagementApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcommerceManagementApplication.class, args);
	}

}
