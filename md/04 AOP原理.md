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

​	所以接下来，就要看看AnnotationAwareAspectJAutoProxyCreator的postProcessBeforeInstantiation()到底做了啥？