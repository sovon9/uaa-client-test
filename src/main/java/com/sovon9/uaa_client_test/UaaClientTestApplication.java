package com.sovon9.uaa_client_test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication // exclude = {DataSourceAutoConfiguration.class}
public class UaaClientTestApplication {

	public static void main(String[] args) {
		SpringApplication.run(UaaClientTestApplication.class, args);
	}

}
