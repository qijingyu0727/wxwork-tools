package com;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.PropertySource;

@SpringBootApplication
@PropertySource(value = {"classpath:properties/wxwork.properties"}, encoding = "utf-8")
@MapperScan(basePackages = {"com.dao"})
public class Application {
	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
}
