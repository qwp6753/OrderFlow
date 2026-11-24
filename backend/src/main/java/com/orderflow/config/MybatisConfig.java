package com.orderflow.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.*;

@Configuration
public class MybatisConfig {
  @Bean
  MybatisPlusInterceptor pagination() {
    var interceptor = new MybatisPlusInterceptor();
    var page = new PaginationInnerInterceptor(DbType.MYSQL);
    page.setMaxLimit(100L);
    interceptor.addInnerInterceptor(page);
    return interceptor;
  }
}
