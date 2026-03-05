package dev.effective.sftppoc;

import dev.effective.sftppoc.config.SftpProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.integration.config.EnableIntegration;

@SpringBootApplication
@EnableIntegration
@EnableConfigurationProperties(SftpProperties.class)
public class SftpPocApplication {

	public static void main(String[] args) {
		SpringApplication.run(SftpPocApplication.class, args);
	}

}
