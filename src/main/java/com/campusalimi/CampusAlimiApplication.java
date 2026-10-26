package com.campusalimi;

import com.campusalimi.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class CampusAlimiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAlimiApplication.class, args);
    }
}
