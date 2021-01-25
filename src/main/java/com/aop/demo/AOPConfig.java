package com.aop.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@EnableAspectJAutoProxy
public class AOPConfig {
    @Bean
    public Service service(){
        return new Service();
    }

    @Bean
    public Aspects aspects(){
        return new Aspects();
    }
}
