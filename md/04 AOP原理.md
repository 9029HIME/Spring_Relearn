# 回顾一下AOP

​	在程序运行期间动态将**某段代码**逻辑切入到**指定方法的指定时机**进行运行。

​	比如原本有A方法，我想在A方法调用之前调用B方法（如操作记录），那我可以通过AOP将B方法切入到A方法之前执行，这样就不用修改A方法的逻辑。

​	**AOP的底层是动态代理**。

​	导入依赖

```xml
<dependency>
    <groupId>org.springframework</groupId>
    <artifactId>spring-aspects</artifactId>
    <version>4.3.12.RELEASE</version>
</dependency>
```

AOP三要素:切点，切面，通知方法，配置（注解驱动）。

切点：被切入的方法，如上面的例子中，A方法即切点。

切面：切入切点的方法，如上面的例子中，B方法即切面。

通知方法：定义切面在切点何时生效，有五种。

配置：在配置类上加上@EnableAspectJAutoProxy，在切面类上配置@Aspect。

​	前置通知：切点执行前调用切面。注解为@Before。

​	后置通知：切点执行后调用切面（不管是否有异常），注解为@After。

​	返回通知：切点正常返回后调用切面，注解为@AfterRunning。

​	异常通知：切点发生异常后调用切面，注解为@AfterThrowing。

​	环绕通知：在切点执行的整个流程中都可以调用切面，直接为@Around。

具体代码与笔记见com/aop/demo，**注意！必须要IOC容器内的类才能使用AOP特性**。



# @EnableAspectJProxy

​	表示开启AOP功能，核心功能是导入了AspectJAutoProxyRegistrar.class。AspectJAutoProxyRegistrar的只要作用是给容器注册了internalAutoProxyCreator = AnnotationAwareAspectJAutoProxyCreator这个bean。

## AnnotationAwareAspectJAutoProxyCreator

​	->AspectJAwareAdvisorAutoProxyCreator

​		->AbstractAdvisorAutoProxyCreator

​			->AbstractAutoProxyCreator implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware

​	最重要是其父父父类实现了一个BeanPostProcessor（用来初始化前后调用自定义方法，具体看02 生命周期.md）和BeanFactoryAware（用来获取beanFactory这个Spring底层组件，具体看02 生命周期.md）。接下来我们从父类到子类查看这两个接口的方法重写情况

​	1.AbstractAutoProxyCreator  → setBeanFactory()

​	2.AbstractAutoProxyCreator  → postProcessBeforeInstantiation()、postProcessAfterInstantiation()

​	3.AbstractAdvisorAutoProxyCreator → setBeanFactory()，其中里面调用了自身定义的initBeanFactory()

​	4.AbstractAdvisorAutoProxyCreator → 无处理器重写

​	5.AspectJAwareAdvisorAutoProxyCreator → 无BeanFactory重写

​	6.AspectJAwareAdvisorAutoProxyCreator → 无处理器重写

​	7.AnnotationAwareAspectJAutoProxyCreator → 重写了3.里AbstractAdvisorAutoProxyCreator定义的initBeanFactory()

​	即AnnotationAwareAspectJAutoProxyCreator在调用接口方法的时候，会调用父父父类AbstractAutoProxyCreator的前置处理与后置处理，父父类的AbstractAdvisorAutoProxyCreator的setBeanFactory()方法，setBeanFactory()方法还会调用自身重写的initBeanFactory()方法。

## AnnotationAwareAspectJAutoProxyCreator注入流程

​	在AbstractAdvisorAutoProxyCreator.setBeanFactory()打上断点，开启debug，流程如下：

​	1.通过AOPConfig创建IOC容器，到了核心的refresh()刷新容器。

​	2.在refresh()里会调用registerBeanPostProcessors()，即注册bean的后置处理器

​	3.registerBeanPostProcessors()调用PostProcessorRegistrationDelegate.registerBeanPostProcessors()时，先获取IOC容器中已定义并需要创建的BeanPostProcessor（**此时还是BeanDefinition**），如上面所说的internalAutoProxyCreator （其实实际对象是AnnotationAwareAspectJAutoProxyCreator）。

![image-20210127210601523](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210127210601523.png)

​	4.接着在PostProcessorRegistrationDelegate.registerBeanPostProcessors()又会对获取的beanName进行区分，区分哪些是实现了Order接口的，哪些没实现，以此来确定注入顺序。

```java
//orderedPostProcessorNames是基于上面postProcessorNames做了区分的集合
Iterator var14 = orderedPostProcessorNames.iterator();

while(var14.hasNext()) {
    String ppName = (String)var14.next();
    BeanPostProcessor pp = (BeanPostProcessor)beanFactory.getBean(ppName, BeanPostProcessor.class);
    orderedPostProcessors.add(pp);
    if (pp instanceof MergedBeanDefinitionPostProcessor) {
        internalPostProcessors.add(pp);
    }
}
```

​	5.最后还是通过IOC的AbstractBeanFactory.doGetBean获取，但由于是第一次获取，所以这里的getSingleton()的getObject()即lambda表达式里用的是createBean()

```java
// AbstractBeanFactory.doGetBean部分源码
if (mbd.isSingleton()) {
    sharedInstance = this.getSingleton(beanName, () -> {
        try {
            return this.createBean(beanName, mbd, args);
        } catch (BeansException var5) {
            this.destroySingleton(beanName);
            throw var5;
        }
    });
```

​	6.也就是说AnnotationAwareAspectJAutoProxyCreator一开始先要获取，发现获取不到后便创建，然后扔到IOC容器里，具体的创建代码在AbstractAutowireCapableBeanFactory.doCreateBean()，以下是代码节选：

![image-20210127213027813](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210127213027813.png)

![image-20210127212835939](C:\Users\Administrator\AppData\Roaming\Typora\typora-user-images\image-20210127212835939.png)

​	创建完后还要进行populateBean为bean赋值，然后就是initializeBean()为bean进行初始化，包括调用BeanPostProcessor（具体看02 生命周期.md）。

​	7.initializeBean()里会调用invokeAwareMethods()，他的作用是调用**bean本身部分Aware接口的方法**。接下来就是调用所有前置处理器 → 执行初始化方法 → 执行所有后置处理器（具体看02 生命周期）

```java
// invokeAwareMethods 源码节选
private void invokeAwareMethods(String beanName, Object bean) {
    if (bean instanceof Aware) {
        if (bean instanceof BeanNameAware) {
            ((BeanNameAware)bean).setBeanName(beanName);
        }

        if (bean instanceof BeanClassLoaderAware) {
            ClassLoader bcl = this.getBeanClassLoader();
            if (bcl != null) {
                ((BeanClassLoaderAware)bean).setBeanClassLoader(bcl);
            }
        }

        if (bean instanceof BeanFactoryAware) {
            ((BeanFactoryAware)bean).setBeanFactory(this);
        }
    }
}
```

​	上面已经说了，AnnotationAwareAspectJAutoProxyCreator本身是BeanFactoryAware的实现类，且setBeanFactory实际调用的是AbstractAdvisorAutoProxyCreator.setBeanFactory()，setBeanFactory()里面调用的initBeanFactory实际调用的是AnnotationAwareAspectJAutoProxyCreator.initBeanFactory()

​	8.最后回到AnnotationAwareAspectJAutoProxyCreator.initBeanFactory()

```java
protected void initBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    super.initBeanFactory(beanFactory);
    if (this.aspectJAdvisorFactory == null) {
        //通过包装beanFactory新建了一个ReflectiveAspectJAdvisorFactory
        this.aspectJAdvisorFactory = new ReflectiveAspectJAdvisorFactory(beanFactory);
    }

    // 再包装beanFactory与aspectJAdvisorFactory新建一个BeanFactoryAspectJAdvisorsBuilderAdapter
    this.aspectJAdvisorsBuilder = new AnnotationAwareAspectJAutoProxyCreator.BeanFactoryAspectJAdvisorsBuilderAdapter(beanFactory, this.aspectJAdvisorFactory);
}
```

​	9.最后经过一系列处理，将已经创建好的AnnotationAwareAspectJAutoProxyCreator添加到BeanPostProcessor集合里，方便下次获取。

## AnnotationAwareAspectJAutoProxyCreator作用

​		AnnotationAwareAspectJAutoProxyCreator是InstantiationAwareBeanPostProcessor这种类型的后置处理器

当上面注册完AnnotationAwareAspectJAutoProxyCreator后，refresh()继续走，走到了finishBeanFactoryInitialization()

```java
// refresh()节选
try {
    this.postProcessBeanFactory(beanFactory);
    StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");
    this.invokeBeanFactoryPostProcessors(beanFactory);
    //这是上面的注入流程
    this.registerBeanPostProcessors(beanFactory);
    beanPostProcess.end();
    this.initMessageSource();
    this.initApplicationEventMulticaster();
    this.onRefresh();
    this.registerListeners();
    //接下来走这里，用来初始化剩下的singleton bean	
    this.finishBeanFactoryInitialization(beanFactory);
    this.finishRefresh();
}
```

接下来遍历beanFactory剩下的singleton bean name，为其创建对象，走到最后还是回到了doGetBean() → createBean()。**注意！看来doGetBean()很重要，里面封装了很多细节，有必要了解一下。**

到了createBean()后，有一行关键代码

```java
// createBean节选
try {
    // 希望让后置处理器返回一个代理对象bean，而非普通bean
    beanInstance = this.resolveBeforeInstantiation(beanName, mbdToUse);
    // 如果能返回一个代理对象，就直接返回，不继续执行了
    if (beanInstance != null) {
        return beanInstance;
    }
} catch (Throwable var10) {
    throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, "BeanPostProcessor before instantiation of bean failed", var10);
}

try {
    // 如果不能，那就老老实实用doCreateBean()来创建bean，doCreateBean()老熟人了，不信你往上翻翻，就是调用beanPostProcessor与initializeBean那部分。
            beanInstance = this.doCreateBean(beanName, mbdToUse, args);
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Finished creating instance of bean '" + beanName + "'");
            }

            return beanInstance;
        } catch (ImplicitlyAppearedSingletonException | BeanCreationException var7) {
            throw var7;
        } catch (Throwable var8) {
            throw new BeanCreationException(mbdToUse.getResourceDescription(), beanName, "Unexpected exception during bean creation", var8);
        }
```

进去看看，他是怎么返回代理对象的

```java
@Nullable
protected Object resolveBeforeInstantiation(String beanName, RootBeanDefinition mbd) {
    Object bean = null;
    if (!Boolean.FALSE.equals(mbd.beforeInstantiationResolved)) {
        if (!mbd.isSynthetic() && this.hasInstantiationAwareBeanPostProcessors()) {
            Class<?> targetType = this.determineTargetType(beanName, mbd);
            if (targetType != null) {
                //让bean调用BeanPostProcessorsBeforeInstantiation()
                bean = this.applyBeanPostProcessorsBeforeInstantiation(targetType, beanName);
                if (bean != null) {
                    //让bean调用applyBeanPostProcessorsAfterInitialization()
                    bean = this.applyBeanPostProcessorsAfterInitialization(bean, beanName);
                }
            }
        }
        mbd.beforeInstantiationResolved = bean != null;
    }

    return bean;
}
```

```java
@Nullable
protected Object applyBeanPostProcessorsBeforeInstantiation(Class<?> beanClass, String beanName) {
    //先在beanPostProcessor缓存里拿到所有的InstantiationAwareBeanPostProcessor
    Iterator var3 = this.getBeanPostProcessorCache().instantiationAware.iterator();

    Object result;
    do {
        if (!var3.hasNext()) {
            // 所有postProcessBeforeInstantiation后置处理器处理后，都不能返回一个代理对象，则直接返回null
            return null;
        }

        InstantiationAwareBeanPostProcessor bp = (InstantiationAwareBeanPostProcessor)var3.next();
        // 执行这个后置处理器的postProcessBeforeInstantiation()方法，返回一个bean
        result = bp.postProcessBeforeInstantiation(beanClass, beanName);
    } while(result == null);

    return result;
}
```

​	那么问题来了，上面我们直到AnnotationAwareAspectJAutoProxyCreator不仅是BeanPostProcessor，还是InstantiationAwareBeanPostProcessor，那么在这一步就会调用AnnotationAwareAspectJAutoProxyCreator的postProcessBeforeInstantiation()方法。那他俩有啥不同呢？

​	BeanPostProcessor：在**创建bean对象时**初始化完成前后调用的

​	InstantiationAwareBeanPostProcessor：在**创建bean对象bean对象前**被尝试调用，用来返回一个代理对象。如果成功返回不为null，则不会调用到BeanPostProcessor了。

​	所以接下来，就要看看AnnotationAwareAspectJAutoProxyCreator的postProcessBeforeInstantiation()到底做了啥？这里先看postProcessBeforeInstantiation()

```java
public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
    // 拿到被处理后的beanName
    Object cacheKey = this.getCacheKey(beanClass, beanName);
    if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
        	// 判断这个bean已经历过增强步骤？ 
        if (this.advisedBeans.containsKey(cacheKey)) {
            return null;
        }
		// 判断这个bean是否实现了Advice，Pointcut，Advisor，AopInfrastructureBean，以及bean是否为切面    	||	应该跳过？ 如果是被切点的类，两个判断都为false，这里不会进if。
        if (this.isInfrastructureClass(beanClass) || this.shouldSkip(beanClass, beanName)) {
            this.advisedBeans.put(cacheKey, Boolean.FALSE);
            return null;
        }
    }

    TargetSource targetSource = this.getCustomTargetSource(beanClass, beanName);
    if (targetSource != null) {
        if (StringUtils.hasLength(beanName)) {
            this.targetSourcedBeans.add(beanName);
        }

        Object[] specificInterceptors = this.getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
        Object proxy = this.createProxy(beanClass, beanName, specificInterceptors, targetSource);
        this.proxyTypes.put(cacheKey, proxy.getClass());
        return proxy;
    } else {
        return null;
    }
}
```

```java
protected boolean shouldSkip(Class<?> beanClass, String beanName) {
    // 找到候选的增强器，增强器即"通知与切面"
    List<Advisor> candidateAdvisors = this.findCandidateAdvisors();
    Iterator var4 = candidateAdvisors.iterator();

    Advisor advisor;
    do {
        if (!var4.hasNext()) {
            // 如果没有一个增强器符合下面的条件，直接判断beanName是否为beanClass的名字+.ORIGINAL
            return super.shouldSkip(beanClass, beanName);
        }
        advisor = (Advisor)var4.next();
        // 增强器是否为AspectJPointcutAdvisor ||  增强器的aspectName是否为bean的名字
    } while(!(advisor instanceof AspectJPointcutAdvisor) || !((AspectJPointcutAdvisor)advisor).getAspectName().equals(beanName));
		
    return true;
}
```

​	如果是被切点的类的话，postProcessBeforeInstantiation直接return了null，被切点类的对象在创建时会老老实实走doCreateBean()了，此时会进入applyBeanPostProcessorsAfterInitialization()，即调用所有BeanPostProcessors的postProcessAfterInitialization()方法，**这里就用到了AbstractAutoProxyCreator.postProcessAfterInitialization()**。

```java
public Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
    if (bean != null) {
        // 老样子 获取bean的名字
        Object cacheKey = this.getCacheKey(bean.getClass(), beanName);
        if (this.earlyProxyReferences.remove(cacheKey) != bean) {
            // 如果这个bean之前没有被代理过的话，就看下是否有必要包装
            return this.wrapIfNecessary(bean, beanName, cacheKey);
        }
    }

    return bean;
}
```

```java
protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
    if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
        return bean;
        // 前面判断过
    } else if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
        return bean;
        // 前面也判断过，不过这里取反了，所以会进这个分支
    } else if (!this.isInfrastructureClass(bean.getClass()) && !this.shouldSkip(bean.getClass(), beanName)) {
        // 获取这个bean的通知与切面（增强器）
        Object[] specificInterceptors = this.getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, (TargetSource)null);
        // 如果有增强器，进入创建代理对象
        if (specificInterceptors != DO_NOT_PROXY) {
            // 加入到需要增强的bean集合里，值为true，即已经历过增强步骤，成功了
            this.advisedBeans.put(cacheKey, Boolean.TRUE);
            // 创建代理对象
            Object proxy = this.createProxy(bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
            this.proxyTypes.put(cacheKey, proxy.getClass());
            return proxy;
            // 如果没有增强器，加入到需要增强的bean集合里，但值为false，即已经历过增强步骤，但失败了
        } else {
            this.advisedBeans.put(cacheKey, Boolean.FALSE);
            return bean;
        }
    } else {
        this.advisedBeans.put(cacheKey, Boolean.FALSE);
        return bean;
    }
}
```

​	所以说，如果这里是要创建被切点对象，应该通过getAdvicesAndAdvisorsForBean()找到这个被切点类的通知（@after等）与切面（增强方法），这里看下如何获取的？

```java
@Nullable
protected Object[] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
    List<Advisor> advisors = this.findEligibleAdvisors(beanClass, beanName);
    return advisors.isEmpty() ? DO_NOT_PROXY : advisors.toArray();
}
```

​	看起来还封装了一层，真正有用的还是findEligibleAdvisors()

```java
protected List<Advisor> findEligibleAdvisors(Class<?> beanClass, String beanName) {
    // 先找到所有增强器
    List<Advisor> candidateAdvisors = this.findCandidateAdvisors();
    // 在调用findAdvisorsThatCanApply()，查找所有增强器中可以应用到beanClass的增强器（这里我猜是解析@Pointcut(value)）
    List<Advisor> eligibleAdvisors = this.findAdvisorsThatCanApply(candidateAdvisors, beanClass, beanName);
    // TODO 这里还不清楚
    this.extendAdvisors(eligibleAdvisors);
    
    if (!eligibleAdvisors.isEmpty()) {
		// 排个序
        eligibleAdvisors = this.sortAdvisors(eligibleAdvisors);
    }

    return eligibleAdvisors;
}
```

​	最后看一下创建代理对象的方法createProxy()

```java
protected Object createProxy(Class<?> beanClass, @Nullable String beanName, @Nullable Object[] specificInterceptors, TargetSource targetSource) {
    if (this.beanFactory instanceof ConfigurableListableBeanFactory) {
        AutoProxyUtils.exposeTargetClass((ConfigurableListableBeanFactory)this.beanFactory, beanName, beanClass);
    }
	// 获取代理工厂
    ProxyFactory proxyFactory = new ProxyFactory();
    proxyFactory.copyFrom(this);
    if (!proxyFactory.isProxyTargetClass()) {
        if (this.shouldProxyTargetClass(beanClass, beanName)) {
            proxyFactory.setProxyTargetClass(true);
        } else {
            this.evaluateProxyInterfaces(beanClass, proxyFactory);
        }
    }

    Advisor[] advisors = this.buildAdvisors(beanName, specificInterceptors);
    proxyFactory.addAdvisors(advisors);
    proxyFactory.setTargetSource(targetSource);
    this.customizeProxyFactory(proxyFactory);
    proxyFactory.setFrozen(this.freezeProxy);
    if (this.advisorsPreFiltered()) {
        proxyFactory.setPreFiltered(true);
    }
	//用代理工厂为beanClass创建代理对象
    return proxyFactory.getProxy(this.getProxyClassLoader());
}
```

​	到了这里，会分为两步走：1.用createAopProxy()创建一个AopProxy，2.用AopProxy的getProxy()为该bean创建代理对象。但是AopProxy有两种实现：1.cglib 2.jdk原生代理对象，具体是哪种要看spring的决定，一般来说没有接口实现的类用的是cglib，反之则是原生代理，当然也可以强制使用cglib。

```java
public AopProxy createAopProxy(AdvisedSupport config) throws AopConfigException {
    if (IN_NATIVE_IMAGE || !config.isOptimize() && !config.isProxyTargetClass() && !this.hasNoUserSuppliedProxyInterfaces(config)) {
        return new JdkDynamicAopProxy(config);
    } else {
        Class<?> targetClass = config.getTargetClass();
        if (targetClass == null) {
            throw new AopConfigException("TargetSource cannot determine target class: Either an interface or a target is required for proxy creation.");
        } else {
            return (AopProxy)(!targetClass.isInterface() && !Proxy.isProxyClass(targetClass) ? new ObjenesisCglibAopProxy(config) : new JdkDynamicAopProxy(config));
        }
    }
}
```

​	这么看来，@EnableAspectJProxy最关键的还是引入了AbstractAutoProxyCreator这个处理器，并调用了postProcessAfterInitialization()这个方法来创建增强后的代理对象。



# 代理对象的方法执行流程

​	既然前面已经用AOP创建了一个代理对象，那么还得看看调用了代理对象的方法后，到底经历了哪些步骤。总体来看可以分为两步：1.获取拦截器链，2.根据拦截器链调度被切方法。

## 获取拦截器链

​	以cglib代理方式为例，调用代理对象的方法时，会被CglibAopProxy.intercept()拦截到。

```java
public Object intercept(Object proxy, Method method, Object[] args, MethodProxy methodProxy) throws Throwable {
    Object oldProxy = null;
    boolean setProxyContext = false;
    Object target = null;
    TargetSource targetSource = this.advised.getTargetSource();

    Object var16;
    try {
        if (this.advised.exposeProxy) {
            oldProxy = AopContext.setCurrentProxy(proxy);
            setProxyContext = true;
        }

        target = targetSource.getTarget();
        Class<?> targetClass = target != null ? target.getClass() : null;
        // 关键步骤1 ：根据ProxyFactory获取拦截器链
        List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);
        Object retVal;
        // 如果拦截器为空，即这个方法没有被切
        if (chain.isEmpty() && Modifier.isPublic(method.getModifiers())) {
            Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
            // 调用原生方法
            retVal = methodProxy.invoke(target, argsToUse);
        } else {
            // 关键步骤2：根据拦截器、代理对象、方法、参数等条件创建一个CglibMethodInvocation，并调用方法。
            retVal = (new CglibAopProxy.CglibMethodInvocation(proxy, target, method, args, targetClass, chain, methodProxy)).proceed();
        }

        retVal = CglibAopProxy.processReturnType(proxy, target, method, retVal);
        var16 = retVal;
    } finally {
        if (target != null && !targetSource.isStatic()) {
            targetSource.releaseTarget(target);
        }

        if (setProxyContext) {
            AopContext.setCurrentProxy(oldProxy);
        }

    }

    return var16;
}
```

​	先看一下拦截器是怎么获取的

```java
public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, @Nullable Class<?> targetClass) {
    AdvisedSupport.MethodCacheKey cacheKey = new AdvisedSupport.MethodCacheKey(method);
    List<Object> cached = (List)this.methodCache.get(cacheKey);
    // 一般第一次获取是为null的
    if (cached == null) {
        cached = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(this, method, targetClass);
        // 获取了方法的拦截器后，将它放入缓存中，以方法为Key
        this.methodCache.put(cacheKey, cached);
    }

    return cached;
}
```

```java
public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Advised config, Method method, @Nullable Class<?> targetClass) {
    AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();
    // 这里的config其实是AOP工厂，通过Config获取所有的增强器
    Advisor[] advisors = config.getAdvisors();
    // 存放结果的list
    List<Object> interceptorList = new ArrayList(advisors.length);
    Class<?> actualClass = targetClass != null ? targetClass : method.getDeclaringClass();
    Boolean hasIntroductions = null;
    Advisor[] var9 = advisors;
    int var10 = advisors.length;

    // 遍历所有config获取的增强器
    for(int var11 = 0; var11 < var10; ++var11) {
        Advisor advisor = var9[var11];
        // 如果增强器是PointcutAdvisor
        if (advisor instanceof PointcutAdvisor) {
            PointcutAdvisor pointcutAdvisor = (PointcutAdvisor)advisor;
            if (config.isPreFiltered() || pointcutAdvisor.getPointcut().getClassFilter().matches(actualClass)) {
                MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
                boolean match;
                // 如果即是PointcutAdvisor 也是 IntroductionAwareMethodMatcher
                if (mm instanceof IntroductionAwareMethodMatcher) {
                    if (hasIntroductions == null) {
                        hasIntroductions = hasMatchingIntroductions(advisors, actualClass);
                    }
				// 用来看增强器与目标方法是否配对（是否切入的是该方法）
                    match = ((IntroductionAwareMethodMatcher)mm).matches(method, actualClass, hasIntroductions);
                } else {
                    // 用来看增强器与目标方法是否配对（是否切入的是该方法）
                    match = mm.matches(method, actualClass);
                }
				// 如果增强器和目标方法配对
                if (match) {
                    // 将增强器转为MethodInterceptor
                    MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
                    if (mm.isRuntime()) {
                        MethodInterceptor[] var17 = interceptors;
                        int var18 = interceptors.length;

                        for(int var19 = 0; var19 < var18; ++var19) {
                            MethodInterceptor interceptor = var17[var19];
                            // 最后将转换好的MethodInterceptor加入到结果集合里
                            interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
                        }
                    } else {
                        // 最后将转换好的MethodInterceptor加入到结果集合里
                        interceptorList.addAll(Arrays.asList(interceptors));
                    }
                }
            }
            // 如果增强器是IntroductionAdvisor
        } else if (advisor instanceof IntroductionAdvisor) {
            IntroductionAdvisor ia = (IntroductionAdvisor)advisor;
            if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
                // 做转换，并加入到结果集合里
                Interceptor[] interceptors = registry.getInterceptors(advisor);
                interceptorList.addAll(Arrays.asList(interceptors));
            }
        } else {
            // 做转换，并加入到结果集合里
            Interceptor[] interceptors = registry.getInterceptors(advisor);
            interceptorList.addAll(Arrays.asList(interceptors));
        }
    }
	// 最终返回结果
    return interceptorList;
}
```

​	可以看到获取的拦截器链主要是将增强器通过registry.getInterceptors()转为Interceptor集合，最后返回，拦截器链本质也只是一个Interceptor集合，接下来就得看看getInterceptors()是怎么将增强器转为拦截器的。

```java
public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
    List<MethodInterceptor> interceptors = new ArrayList(3);
    Advice advice = advisor.getAdvice();
    // 如果增强器本身就是MethodInterceptor，直接强转并加入
    if (advice instanceof MethodInterceptor) {
        interceptors.add((MethodInterceptor)advice);
    }

    Iterator var4 = this.adapters.iterator();

    // 遍历所有适配器
    while(var4.hasNext()) {
        AdvisorAdapter adapter = (AdvisorAdapter)var4.next();
        //如果适配器能够适配这个增强器，则用适配器将增强器转为MethodInterceptor并加入(其实就是看增强器是不是适配器的派生)
        if (adapter.supportsAdvice(advice)) {
            interceptors.add(adapter.getInterceptor(advisor));
        }
    }

    if (interceptors.isEmpty()) {
        throw new UnknownAdviceTypeException(advisor.getAdvice());
    } else {
        return (MethodInterceptor[])interceptors.toArray(new MethodInterceptor[0]);
    }
}
```

​	除了**注册器**的getInterceptor(增强器)外，还有**适配器**的getInterceptor(增强器)，一共有三个适配器，看一下这三个适配器到底做了什么。

​	AfterReturningAdviceAdapter：

```java
public MethodInterceptor getInterceptor(Advisor advisor) {
    AfterReturningAdvice advice = (AfterReturningAdvice)advisor.getAdvice();
    return new AfterReturningAdviceInterceptor(advice);
}
```

​	MethodBeforeAdviceAdapter：

```java
public MethodInterceptor getInterceptor(Advisor advisor) {
    MethodBeforeAdvice advice = (MethodBeforeAdvice)advisor.getAdvice();
    return new MethodBeforeAdviceInterceptor(advice);
}
```

​	ThrowsAdviceAdapter：

```java
public MethodInterceptor getInterceptor(Advisor advisor) {
    return new ThrowsAdviceInterceptor(advisor.getAdvice());
}
```

​	这三个适配器都是**MethodInterceptor的派生**，所谓getInterceptor()只是将不属于MethodInterceptor的Advice(增强器类型)包装为这三个适配器的其中一种，然后返回。

## 执行拦截器链

​	上面解释了如何从增强器 → 获取拦截器后，接下来就要基于拦截器链对目标方法进行拦截调用。