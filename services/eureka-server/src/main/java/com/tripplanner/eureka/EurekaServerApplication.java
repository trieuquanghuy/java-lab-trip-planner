// Source: 00-RESEARCH.md Pattern 4 (lines 572-587). Package per D-28 / Convention C1.
// @EnableEurekaServer is what makes this Spring Boot app a Netflix Eureka registry server
// (vs a client). The standalone-server config (register-with-eureka=false, fetch-registry=false)
// lives in src/main/resources/application.yml — see Task 5.2.
package com.tripplanner.eureka;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@SpringBootApplication
@EnableEurekaServer
public class EurekaServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(EurekaServerApplication.class, args);
    }
}
