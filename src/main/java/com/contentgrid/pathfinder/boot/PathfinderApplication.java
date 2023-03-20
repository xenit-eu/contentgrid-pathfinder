package com.contentgrid.pathfinder.boot;

import com.contentgrid.pathfinder.PathfinderConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import(PathfinderConfiguration.class)
public class PathfinderApplication {

	public static void main(String[] args) {
		SpringApplication.run(PathfinderApplication.class, args);
	}

}
