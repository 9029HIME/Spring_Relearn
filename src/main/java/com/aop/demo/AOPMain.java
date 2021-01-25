package com.aop.demo;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class AOPMain {
    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AOPConfig.class);
        Service bean = context.getBean(Service.class);
        bean.div(1,2);
        System.out.println("=======================================");
        bean.plus(1,0);
    }
}
