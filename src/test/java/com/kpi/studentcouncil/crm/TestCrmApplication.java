package com.kpi.studentcouncil.crm;

import org.springframework.boot.SpringApplication;

public class TestCrmApplication {

	public static void main(String[] args) {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
		SpringApplication.from(CrmApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
