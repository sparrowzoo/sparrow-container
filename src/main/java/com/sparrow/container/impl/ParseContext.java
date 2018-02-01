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
import com.sparrow.constant.magic.SYMBOL;
import com.sparrow.container.BeanDefinition;
import com.sparrow.core.TypeConverter;
import com.sparrow.exception.DuplicateActionMethodException;
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
 * Created by harry on 2018/1/19.
 */
class ParseContext {

    Logger logger = LoggerFactory.getLogger(this.getClass());

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
     * @param beanName 依赖bean name
     * @param reference 依赖的bean
     */
    <T> void setReference(T currentObject, String beanName, T reference)
        throws Exception {
        // set bean class
        Class<?> setBeanClazz = null;
        if (reference != null) {
            setBeanClazz = reference.getClass();
        }
        if (setBeanClazz == null) {
            setBeanClazz = this.beanFactoryCache.get(beanName).getClass();
        }
        Class<?> currentClass = currentObject.getClass();
        Method method = null;
        // set method name
        String setBeanMethod = StringUtility.getSetMethodNameByField(beanName);
        try {
            method = currentClass.getMethod(setBeanMethod, setBeanClazz);
        } catch (NoSuchMethodException e) {
            Class<?>[] interfaces = setBeanClazz.getInterfaces();
            if (interfaces.length == 0 && setBeanClazz.getSuperclass() != null) {
                interfaces = setBeanClazz.getSuperclass().getInterfaces();
            }
            for (Class<?> interfaceClazz : interfaces) {
                try {
                    method = currentClass.getMethod(setBeanMethod,
                        interfaceClazz);
                    break;
                } catch (NoSuchMethodException ex) {
                    Class<?>[] superClass = interfaceClazz.getInterfaces();
                    if (superClass != null && superClass.length > 0) {
                        try {
                            method = currentClass.getMethod(setBeanMethod,
                                superClass[0]);
                        } catch (NoSuchMethodException e1) {
                            logger.error(setBeanMethod
                                + " method not found!", e1);
                        } catch (NullPointerException e2) {
                            logger.error(interfaceClazz
                                + " interface not found!", e2);
                        }
                    }
                }
            }
        }
        if (method != null) {
            method.invoke(currentObject, reference);
        }
    }

    /**
     * 注入
     *
     * @param currentObject 对象
     * @param propertyName 依赖
     * @param value value placeHolderKey place hold key 由maven pom 管理
     */
    <T> void setValue(T currentObject, String propertyName,
        String value) throws InvocationTargetException, IllegalAccessException {
        // set方法
        String setBeanMethod = StringUtility.getSetMethodNameByField(propertyName);
        Class<?> currentClass = currentObject.getClass();

        Method method;
        try {
            method = currentClass.getMethod(setBeanMethod, String.class);
            method.invoke(currentObject, value);
        } catch (NoSuchMethodException e) {
            try {
                method = currentClass.getMethod(setBeanMethod, Integer.class);
                method.invoke(currentClass, Integer.valueOf(value));
            } catch (NoSuchMethodException e2) {
                try {
                    method = currentClass.getMethod(setBeanMethod, Boolean.class);
                    method.invoke(currentClass, Boolean.valueOf(value));
                } catch (NoSuchMethodException e1) {
                    logger.error("no method", e1);
                }
            }
        }
    }

    protected Object getInstance(String constructorArg,
        Class<?> beanClass) throws Exception {
        if (StringUtility.isNullOrEmpty(constructorArg)) {
            return beanClass.newInstance();
        }
        //是否存在对象
        Object constructorObj = null;
        Constructor constructor;
        if (!constructorArg.contains(SYMBOL.COMMA)) {
            constructorObj = this.getBean(constructorArg);
        }

        //如果是对象
        if (constructorObj != null) {
            //是否有该对象的构造
            constructor = beanClass.getConstructor(constructorObj.getClass());
            if (constructor == null) {
                constructor = beanClass.getConstructor(constructorObj.getClass().getInterfaces()[0]);
            }
            if (constructor == null) {
                constructor = beanClass.getConstructor(String.class);
                if (constructor != null) {
                    constructorObj = constructorArg;
                }
            }
        } else {
            constructor = beanClass.getConstructor(String.class);
            if (constructor != null) {
                constructorObj = constructorArg;
            }
        }
        if (constructor != null) {
            return constructor.newInstance(constructorObj);
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
        Method[] methods = beanClass.getDeclaredMethods();
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

    /**
     * bean definition cache
     *
     * @param beanName xml config
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
        Method[] methods = beanClass.getDeclaredMethods();
        for (Method method : methods) {
            String methodName = method.getName();
            if (methodName.startsWith("get")) {
                typeConverterList.add(new TypeConverter(StringUtility.getFieldByGetMethod(methodName), method.getReturnType()));
            }
        }
        this.fieldCache.put(clazzName, typeConverterList);
    }
}
