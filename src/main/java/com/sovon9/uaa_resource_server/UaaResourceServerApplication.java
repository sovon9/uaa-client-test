package com.sovon9.uaa_resource_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication // exclude = {DataSourceAutoConfiguration.class}
public class UaaResourceServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(UaaResourceServerApplication.class, args);
	}

}
