package com.orderflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@MapperScan("com.orderflow.**.mapper")
public class OrderFlowApplication {
  public static void main(String[] args) {
    SpringApplication.run(OrderFlowApplication.class, args);
  }
}