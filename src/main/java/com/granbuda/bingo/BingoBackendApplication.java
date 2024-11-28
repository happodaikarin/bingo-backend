package com.granbuda.bingo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // Habilita la programación de tareas
public class BingoBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BingoBackendApplication.class, args);
	}

}
