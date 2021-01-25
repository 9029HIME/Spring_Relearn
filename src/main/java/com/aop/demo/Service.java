package com.aop.demo;

public class Service {

    public int div(int a,int b){
        return a/b;
    }

    public Integer plus(int a,int b){
        if(b==0){
            throw new RuntimeException();
        }
        return a+b;
    }
}
