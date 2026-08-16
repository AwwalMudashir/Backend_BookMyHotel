package com.project.Backend_BookMyHotel;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@OpenAPIDefinition(
		info = @Info(
				title = "Book My Hotel Documentation",
				description = "Backend REST APIs for the Book My Hotel app",
				version = "v1.0",
				contact = @Contact(
						name = "Awwal Mudashir",
						email = "awwalmudashir@gmail.com"
				)
		)
)
@EnableCaching
@EnableAsync
public class BackendBookMyHotelApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendBookMyHotelApplication.class, args);
	}

}
