package com.inn.automate;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.annotation.PostConstruct;

@SpringBootApplication
public class AutomationServicesApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutomationServicesApplication.class, args);
	}

	@PostConstruct
	public void init() {
		// Setup ChromeDriver automatically
		WebDriverManager.chromedriver().setup();
		System.out.println("ChromeDriver setup completed");
	}
}