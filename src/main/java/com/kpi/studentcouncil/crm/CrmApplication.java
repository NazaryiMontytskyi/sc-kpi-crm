package com.kpi.studentcouncil.crm;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CrmApplication {

	public static void main(String[] args) {
		// Store and exchange time in UTC; PostgreSQL rejects legacy zone ids such as Europe/Kiev.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(CrmApplication.class, args);
	}

}
