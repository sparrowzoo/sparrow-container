/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sparrow.container.impl;

import com.sparrow.cg.Generator4MethodAccessor;
import com.sparrow.cg.MethodAccessor;
import com.sparrow.cg.PropertyNamer;
import com.sparrow.constant.SYS_OBJECT_NAME;
import com.sparrow.protocol.constant.magic.SYMBOL;
import com.sparrow.container.BeanDefinition;
import com.sparrow.core.TypeConverter;
import com.sparrow.exception.DuplicateActionMethodException;
import com.sparrow.utility.Config;
import com.sparrow.utility.StringUtility;

import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * created by harry
 * on 2018/1/19.
 */
class ParseContext {

    Logger logger = LoggerFactory.getLogger(this.getClass());

    String xmlName;
    String systemConfigPath;

    static final String DTD_FILE_NAME = "beanFactory.dtd";
    static final String NAME = "name";
    static final String VALUE = "value";
    static final String REF = "ref";
    static final String SCOPE = "scope";
    static final String CLASS_NAME = "class";
    static final String BEAN = "bean";
    static final String IMPORT = "import";
    static final String BEANS = "beans";
    static final String SINGLETON = "singleton";
    static final String PROPERTY = "property";
    static final String CONSTRUCTOR_ARG = "constructor-arg";
    static final String CONTROLLER = "controller";
    static final String INTERCEPTOR = "interceptor";
    static final String REMOTE = "remote";

    /**
     * bean definition缓存
     */
    final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<String, BeanDefinition>();

    private Generator4MethodAccessor generator4MethodAccessor = null;
    /**
     * 对象缓存(ALL 包含typeBeanFactory)
     */
    final Map<String, Object> beanFactoryCache = new ConcurrentHashMap<String, Object>();
    /**
     * impl 生成的代理bean的缓存
     */
    final Map<String, MethodAccessor> proxyBeanCache = new ConcurrentHashMap<String, MethodAccessor>();

    /**
     * 对象的set方法缓存
     */
    final Map<String, List<Method>> setMethods = new ConcurrentHashMap<String, List<Method>>();

    /**
     * 对象的get方法缓存
     */
    final Map<String, List<Method>> getMethods = new ConcurrentHashMap<String, List<Method>>();
    /**
     * 实体的field 访问方法缓存
     */
    final Map<String, List<TypeConverter>> fieldCache = new ConcurrentHashMap<String, List<TypeConverter>>();
    /**
     * controller实体对象的操作方法缓存
     */
    final Map<String, Map<String, Method>> controllerMethodCache = new ConcurrentHashMap<String, Map<String, Method>>();
    /**
     * bean分类
     */
    final Map<String, Map<String, Object>> typeBeanFactory = new ConcurrentHashMap<String, Map<String, Object>>();

    @SuppressWarnings("unchecked")
    public <T> T getBean(SYS_OBJECT_NAME objectName) {
        String defaultBeanName = StringUtility.toHump(objectName.name().toLowerCase(), "_");
        String beanName = Config.getValue(objectName.name().toLowerCase(), defaultBeanName);
        T obj = this.getBean(beanName);
        if (obj == null) {
            throw new RuntimeException(beanName + " not exist,please config [" + defaultBeanName + "] in " + this.systemConfigPath);
        }
        return obj;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(String beanName) {
        if (beanName == null) {
            return null;
        }
        // 先从缓存中获取，可能该对象已经被初始化
        T bean = (T) this.beanFactoryCache.get(beanName);
        if (bean != null) {
            return bean;
        }
        // 如果没有被初始化获取该类的元数据
        if (this.beanDefinitionMap.get(beanName) == null) {
            logger.warn(beanName + " object null at SparrowContainerImpl");
            return null;
        }
        try {
            // 类的元数据
            BeanDefinition beanDefinition = this.beanDefinitionMap
                    .get(beanName);
            // 获取当前类
            Class<?> beanClass = beanDefinition.getBeanClass();
            // 初始化当前对象
            T currentObject = (T) beanClass.newInstance();
            // 初始化失败
            if (currentObject == null) {
                logger.warn(beanClass.getClass().getName() + "null");
                return null;
            }
            // 注入依赖对象
            if (beanDefinition.getRelyOnClass().size() != 0) {
                Iterator<String> bit = beanDefinition.getRelyOnClass()
                        .keySet().iterator();
                String key;
                while (bit.hasNext()) {
                    key = bit.next();
                    this.setReference(currentObject, key, this.getBean(key));
                }
            }
            return currentObject;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 注入
     *
     * @param currentObject 对象
     * @param beanName      依赖bean name
     * @param reference     依赖的bean
     */
    <T> void setReference(T currentObject, String beanName, T reference)
            throws Exception {
        Class<?> currentClass = currentObject.getClass();
        List<Method> methods = this.setMethods.get(currentClass.getSimpleName());
        // set方法
        String setBeanMethod =PropertyNamer.setter(beanName);
        for (Method method : methods) {
            if (!method.getName().equals(setBeanMethod)) {
                continue;
            }
            Class parameterType = method.getParameterTypes()[0];
            if (!parameterType.isAssignableFrom(reference.getClass())) {
                continue;
            }
            method.invoke(currentObject, reference);
        }
    }

    /**
     * 注入
     *
     * @param currentObject 对象
     * @param propertyName  依赖
     * @param value         value placeHolderKey place hold key 由maven pom 管理
     */
    <T> void setValue(T currentObject, String propertyName,
                      String value) throws InvocationTargetException, IllegalAccessException {
        // set方法
        String setBeanMethod =PropertyNamer.setter(propertyName);
        Class<?> currentClass = currentObject.getClass();
        List<Method> methods = this.setMethods.get(currentClass.getSimpleName());
        for (Method method : methods) {
            if (!method.getName().equals(setBeanMethod)) {
                continue;
            }
            Class parameterType = method.getParameterTypes()[0];
            method.invoke(currentObject, new TypeConverter(parameterType).convert(value));
            return;
        }
    }

    protected Object getInstance(String constructorArg,
                                 Class<?> beanClass) throws Exception {
        if (StringUtility.isNullOrEmpty(constructorArg)) {
            return beanClass.newInstance();
        }
        String[] argArray = constructorArg.split(SYMBOL.COMMA);
        Constructor[] constructors = beanClass.getConstructors();
        for (Constructor constructor : constructors) {
            if (constructor.getParameterTypes().length != argArray.length) {
                continue;
            }
            Object[] args = new Object[argArray.length];
            Class[] constructorParameterTypes = constructor.getParameterTypes();
            for (int i = 0; i < constructorParameterTypes.length; i++) {
                Object beanArg = this.getBean(argArray[i]);
                if (beanArg != null && beanArg.getClass().equals(constructorParameterTypes[i])) {
                    args[i] = beanArg;
                    continue;
                }
                args[i] = new TypeConverter(constructorParameterTypes[i]).convert(argArray[i]);
            }
            return constructor.newInstance(args);
        }
        return null;
    }

    private Generator4MethodAccessor getGenerator4MethodAccessor() {
        if (this.generator4MethodAccessor != null) {
            return this.generator4MethodAccessor;
        }
        this.generator4MethodAccessor = this.getBean("generator4MethodAccessor");
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

    void assembleController(String beanName, String controller, Class<?> beanClass) {
        if (!Boolean.TRUE.toString().equalsIgnoreCase(controller)) {
            return;
        }
        Method[] methods = beanClass.getMethods();
        Map<String, Method> methodMap = new HashMap<String, Method>(methods.length);
        for (Method method : methods) {
            if (method.getModifiers() == Modifier.PRIVATE) {
                continue;
            }
            if (methodMap.containsKey(method.getName())) {
                throw new DuplicateActionMethodException("Duplicate for the method name " + beanName + " " + method.getName() + "!");
            }
            methodMap.put(method.getName(), method);
        }
        this.controllerMethodCache.put(beanName, methodMap);
    }

    void cacheGetAndSetMethods(Class beanClass) {
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

    /**
     * bean definition cache
     *
     * @param beanName  xml config
     * @param beanClass class
     */
    void cacheBeanDefinition(String beanName, Class beanClass) {
        String clazzName = beanClass.getSimpleName();
        BeanDefinition beanDefinition = new BeanDefinition(beanClass);
        this.beanDefinitionMap.put(beanName, beanDefinition);
        // 如果是非单例对象则生成代理访问对象，提高反射效率
        // 除实体对象外全部为非单例对象
        MethodAccessor methodAccessor = this.getGenerator4MethodAccessor().newMethodAccessor(beanClass);
        this.proxyBeanCache.put(beanClass.getSimpleName(), methodAccessor);
        // 初始化bean 的get set 方法
        // 初始化bean 的get set 方法
        List<TypeConverter> typeConverterList = new ArrayList<TypeConverter>();
        Method[] methods = beanClass.getMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.startsWith("get")) {
                typeConverterList.add(new TypeConverter(StringUtility.getFieldByGetMethod(methodName), method.getReturnType()));
            }
        }
        this.fieldCache.put(clazzName, typeConverterList);
    }
}
