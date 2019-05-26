package com.sparrow.container;

import com.sparrow.cg.Generator4MethodAccessor;
import com.sparrow.cg.MethodAccessor;
import com.sparrow.cg.PropertyNamer;
import com.sparrow.constant.SYS_OBJECT_NAME;
import com.sparrow.core.Pair;
import com.sparrow.core.TypeConverter;
import com.sparrow.exception.DuplicateActionMethodException;
import com.sparrow.utility.Config;
import com.sparrow.utility.StringUtility;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author by harry
 */
public abstract class AbstractContainer implements Container {

    private Logger logger= LoggerFactory.getLogger(this.getClass());

    protected String systemConfigPath;

    protected SimpleSingletonRegistry singletonRegistry=new SimpleSingletonRegistry();

    private ControllerRegister controllerRegister=new ControllerRegister();

    protected InterceptorRegistry interceptorRegistry=new InterceptorRegistry();

    protected SimpleProxyBeanRegistry proxyBeanRegistry=new SimpleProxyBeanRegistry();

    protected SimpleBeanDefinitionRegistry beanDefinitionRegistry=new SimpleBeanDefinitionRegistry();

    private Generator4MethodAccessor generator4MethodAccessor = null;


    /**
     * controller class-name-->method-name-->method
     */
    final Map<String, Map<String, Method>> controllerMethodCache = new ConcurrentHashMap<String, Map<String, Method>>();

    /**
     * class-name-->type converter list
     */
    final Map<String, List<TypeConverter>> fieldCache = new ConcurrentHashMap<String, List<TypeConverter>>();


    /**
     * class-name -->set method list
     */
    final Map<String, List<Method>> setMethods = new ConcurrentHashMap<String, List<Method>>();

    /**
     * class-name -->get method list
     */
    final Map<String, List<Method>> getMethods = new ConcurrentHashMap<String, List<Method>>();


    @Override public FactoryBean getSingletonRegister() {
        return singletonRegistry;
    }

    @Override public FactoryBean getControllerRegister() {
        return controllerRegister;
    }

    @Override public FactoryBean getProxyBeanRegister() {
        return proxyBeanRegistry;
    }

    @Override public FactoryBean getInterceptorRegister() {
        return interceptorRegistry;
    }

    @Override public MethodAccessor getProxyBean(Class<?> clazz) {
        return this.proxyBeanRegistry.getObject(clazz.getSimpleName());
    }

    @Override public List<TypeConverter> getFieldList(Class clazz) {
        return this.fieldCache.get(clazz.getSimpleName());
    }

    @Override public Map<String, Method> getControllerMethod(String clazzName) {
        return controllerMethodCache.get(clazzName);
    }

    public void initMethod(BeanDefinition bd) throws ClassNotFoundException {
        Class beanClass = Class.forName(bd.getBeanClassName());
        Method[] methods = beanClass.getMethods();
        List<Method> setMethods = new ArrayList<Method>(methods.length / 2);
        List<Method> getMethods = new ArrayList<Method>(methods.length / 2);
        for (Method method : methods) {
            if (PropertyNamer.isSetter(method.getName())) {
                setMethods.add(method);
                continue;
            }
            getMethods.add(method);
        }
        this.setMethods.put(beanClass.getSimpleName(), setMethods);
        this.getMethods.put(beanClass.getSimpleName(), getMethods);
    }

    @Override public <T> T getBean(String beanName){
        T o= (T)singletonRegistry.getObject(beanName);
        if(o!=null){
            return o;
        }
        BeanDefinition bd=beanDefinitionRegistry.getObject(beanName);
        if(bd==null){
            return null;
        }
        try {
            o = (T) this.instance(bd);
        }
        catch (Throwable e){
            throw new RuntimeException(e);
        }
        return o;
    }

     public Pair<Class[],Object[]> getConstructorTypes(BeanDefinition bd) {
         TreeMap<Integer, ValueHolder> argMap = bd.getConstructorArgsMap();
         Class[] types = new Class[argMap.size()];
         Object[] args = new Object[argMap.size()];
         for (Integer index : argMap.keySet()) {
             ValueHolder valueHolder = argMap.get(index);
             Object value = valueHolder.getValue();
             Class clazz = valueHolder.getType();
             if (valueHolder.isRef()) {
                 value = singletonRegistry.getObject(value.toString());
                 clazz = value.getClass();
             }
             else {
                 value=new TypeConverter(valueHolder.getName(),value,clazz).convert();
             }

             types[index-1] = clazz;
             args[index-1] = value;
         }

         return Pair.create(types, args);
     }


    private  <T> void set(T currentObject, String beanName, Object val)
        throws Exception {
        Class<?> currentClass = currentObject.getClass();
        List<Method> methods = this.setMethods.get(currentClass.getSimpleName());
        // set方法
        String setBeanMethod = PropertyNamer.setter(beanName);
        for (Method method : methods) {
            if (!method.getName().equals(setBeanMethod)) {
                continue;
            }
            Class parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(val.getClass())) {

                Object v = new TypeConverter("", val, parameterType).convert();
                if (v == null) {
                    continue;
                } else {
                    val = v;
                }
            }
            method.invoke(currentObject, val);
            return;
        }
    }

    protected Object instance(BeanDefinition bd) throws Exception {
        Object instance=null;
        Class clazz=Class.forName(bd.getBeanClassName());
        if(bd.getConstructorArgsMap()!=null&&bd.getConstructorArgsMap().size()>0){
            Pair<Class[],Object[]> pair=this.getConstructorTypes(bd);
            Constructor constructor= clazz.getConstructor(pair.getFirst());
            instance= constructor.newInstance(pair.getSecond());
        }
        else {
            instance= clazz.newInstance();
        }

        List<ValueHolder> valueHolders= bd.getPropertyValues();
        for(ValueHolder valueHolder:valueHolders){
            Object value=valueHolder.getValue();
            if (valueHolder.isRef()) {
                value = singletonRegistry.getObject(value.toString());
            }
            this.set(instance,valueHolder.getName(),value);
        }
        return instance;
    }


    @Override public <T> T getBean(SYS_OBJECT_NAME objectName) {
        String defaultBeanName = StringUtility.toHump(objectName.name().toLowerCase(), "_");
        String beanName = Config.getValue(objectName.name().toLowerCase(), defaultBeanName);
        T obj = this.getBean(beanName);
        if (obj == null) {
            throw new RuntimeException(beanName + " not exist,please config [" + defaultBeanName + "] in " + this.systemConfigPath);
        }
        return obj;
    }

    protected void initProxyBean(Class beanClass) {
        String clazzName = beanClass.getSimpleName();
        MethodAccessor methodAccessor = this.getGenerator4MethodAccessor().newMethodAccessor(beanClass);
        this.proxyBeanRegistry.pubObject(beanClass.getSimpleName(), methodAccessor);
        List<TypeConverter> typeConverterList = new ArrayList<TypeConverter>();
        Method[] methods = beanClass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.startsWith("get")) {
                typeConverterList.add(new TypeConverter(PropertyNamer.methodToProperty(methodName), method.getReturnType()));
            }
        }
        this.fieldCache.put(clazzName, typeConverterList);
    }

    private Generator4MethodAccessor getGenerator4MethodAccessor() {
        if (this.generator4MethodAccessor != null) {
            return this.generator4MethodAccessor;
        }
        this.generator4MethodAccessor = (Generator4MethodAccessor) this.singletonRegistry.getObject("generator4MethodAccessor");
        if (this.generator4MethodAccessor != null) {
            return this.generator4MethodAccessor;
        }
        try {
            generator4MethodAccessor = (Generator4MethodAccessor) Class.forName("com.sparrow.cg.impl.Generator4MethodAccessorImpl").newInstance();
        } catch (Exception e) {
            logger.error("can't find class com.sparrow.cg.impl.Generator4MethodAccessorImpl", e);
        }
        return generator4MethodAccessor;
    }

    protected void assembleController(String beanName,Object o) {
        Class clazz=o.getClass();
        Method[] methods = clazz.getMethods();
        Map<String, Method> methodMap = new HashMap<String, Method>(methods.length);
        for (Method method : methods) {
            if (method.getModifiers() == Modifier.PRIVATE) {
                continue;
            }
            if(method.getDeclaringClass().equals(Object.class)){
                continue;
            }
            if (methodMap.containsKey(method.getName())) {
                throw new DuplicateActionMethodException("Duplicate for the method name " + beanName + " " + method.getName() + "!");
            }
            methodMap.put(method.getName(), method);
        }
        this.controllerMethodCache.put(beanName, methodMap);
        this.controllerRegister.pubObject(beanName,o);
    }
}
