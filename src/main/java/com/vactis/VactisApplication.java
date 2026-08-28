package com.vactis;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VactisApplication {

    public static void main(String[] args) {
        SpringApplication.run(VactisApplication.class, args);
    }

}
