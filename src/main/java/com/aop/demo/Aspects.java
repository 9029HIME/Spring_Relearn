package com.aop.demo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;

@Aspect
public class Aspects {

    /*
    定义切点，我假设切点为Service类的div方法与plus方法
     */
    @Pointcut("execution(public int com.aop.demo.Service.div(int,int))")
    public void divCut(){};

    @Pointcut("execution(public Integer com.aop.demo.Service.plus(int,int))")
    public void plusCut(){};

    /**
     * 切面的每一个方法除了无参声明外，都提供第一个参数为JoinPoint的声明
     * JoinPoint能够获取切点方法的所有信息，如参数，所属注解，所属类信息，所属类注解等
     */

    /*
    定义前置通知与切面,value为切点所在的方法名
     */
    @Before("divCut()")
    public void before(JoinPoint joinPoint){
        System.out.println("div方法前被调用");
    }

    /*
   定义后置通知与切面,value为切点所在的方法名
    */
    @After("divCut()")
    public void after(JoinPoint joinPoint){
        System.out.println("div方法后被调用（不管是否有异常）");
    }

    /*
   定义返回通知与切面,value为切点所在的方法名
   异常通知切面还能多声明一个参数T result，但是注解必须用returning声明用t来接收返回值。
    */
    @AfterReturning(value = "divCut()",returning = "result")
    public void returning(JoinPoint joinPoint,int result){
        System.out.println("div方法返回函数后调用,结果为"+result);
    }

    /*
   定义异常通知与切面,value为切点所在的方法名。
   异常通知切面还能多声明一个参数Exception e，但是注解必须用throwing声明用e来接收异常。
    */
    @AfterThrowing(value = "divCut()",throwing = "e")
    public void exception(JoinPoint joinPoint,Exception e){
        e.printStackTrace();
        System.out.println("div方法抛出异常后被调用");
    }

    /*
    定义环绕通知，环绕通知的切面需要声明参数ProceedingJoinPoint pjp，调用pjp的方法pjp.process()可以直接调用切点
     */
    @Around("plusCut()")
    public Object around(ProceedingJoinPoint pjp){
        //获取切面方法的参数
        Object[] args = pjp.getArgs();
        Object proceed = null;
        try {
            System.out.println("plus方法调用前的前置通知");
            proceed  = pjp.proceed(args);
            System.out.println("plus方法返回后的返回通知，返回值为："+(Integer)proceed);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.out.println("plus方法触发异常后的异常通知");
        }
        System.out.println("plus方法调用后的后置通知");
        return proceed;
    }



}
