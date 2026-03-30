// YunmuApplication.java
package com.yunmu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class YunmuApplication {
    public static void main(String[] args) {
        SpringApplication.run(YunmuApplication.class, args);
    }
}