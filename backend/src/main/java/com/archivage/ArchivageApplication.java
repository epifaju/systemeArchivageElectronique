package com.archivage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class ArchivageApplication {

    public static void main(String[] args) {
        SpringApplication.run(ArchivageApplication.class, args);
    }
}
