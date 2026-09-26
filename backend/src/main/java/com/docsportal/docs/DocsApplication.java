package com.docsportal.docs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class DocsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocsApplication.class, args);
    }

}
