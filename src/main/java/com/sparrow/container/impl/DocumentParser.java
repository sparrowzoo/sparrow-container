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

import com.sparrow.constant.magic.SYMBOL;
import com.sparrow.container.BeanDefinition;
import com.sparrow.enums.CONTAINER;
import com.sparrow.utility.StringUtility;
import org.w3c.dom.Element;

import java.util.HashMap;

/**
 * @author haryy
 * @date 2018/1/19
 */
public class DocumentParser extends ParseContext {

    void parseProperty(Element element) throws Exception {
        String propertyName = element.getAttribute(NAME).trim();
        String refBeanName = element.getAttribute(REF);
        String value = element.getAttribute(VALUE);
        Element parentElement = (Element) element.getParentNode();
        String parentBeanName = parentElement.getAttribute(NAME);
        String scope = parentElement.getAttribute(SCOPE)
                .trim().toLowerCase();
        // 如果当前对象的父对象是单例
        // 则注入该对象
        if (SINGLETON.equals(scope) || SYMBOL.EMPTY.equals(scope)) {
            setSingletonProperty(propertyName, refBeanName, value, parentBeanName);
            return;
        }

        // 如果非单例对象 则将类定义压入到缓存
        BeanDefinition beanDefinition = this.beanDefinitionMap
                .get(refBeanName);
        if (beanDefinition == null) {
            beanDefinition = new BeanDefinition(this.getBean(refBeanName)
                    .getClass());
        }
        // 获取依赖类并压入缓存
        this.beanDefinitionMap.get(parentBeanName).getRelyOnClass()
                .put(refBeanName, beanDefinition);
    }


    private void setSingletonProperty(String propertyName, String refBeanName, String value, String parentBeanName) throws Exception {
        Object parent = this.getBean(parentBeanName);
        if (StringUtility.isNullOrEmpty(refBeanName)) {
            this.setValue(parent, propertyName,
                    value);
            return;
        }

        // 引用必须在该对象初始化之前先被初始化
        if (this.beanFactoryCache.get(refBeanName) == null) {
            logger.error("error: ref bean "
                    + refBeanName
                    + " must be initialization! because " + parentBeanName + " Class is Singleton");
            System.exit(0);
        }

        Object ref = this.getBean(refBeanName);
        // 注入该对象
        this.setReference(parent, propertyName, ref);
    }

    /**
     * 读xml标签初始化bean 并加入到bean factory 和bean definition factory
     */
    Object parseBean(Element element, String beanName) {
        // 是否为单子实例
        String scope = element.getAttribute(SCOPE);
        // class名
        String className = element.getAttribute(CLASS_NAME);
        //构造参数
        String constructorArg = element.getAttribute(CONSTRUCTOR_ARG);
        //controller名
        String controller = element.getAttribute(CONTROLLER);
        //拦截器
        String interceptor = element.getAttribute(INTERCEPTOR);
        //远程bean
        String remote = element.getAttribute(REMOTE);

        String container = null;

        Class<?> beanClass;
        try {
            beanClass = Class.forName(className);
        } catch (Exception e) {
            logger.error("bean name error :" + beanName, e);
            return null;
        }

        if (!StringUtility.isNullOrEmpty(scope) && !SINGLETON.equalsIgnoreCase(scope)) {
            // 如果不是单例则缓存该类的元数据
            this.cacheBeanDefinition(beanName, beanClass);
            return null;
        }
        try {
            // 如果是单例对象
            Object instance = getInstance(constructorArg, beanClass);
            if (instance == null) {
                return null;
            }

            //缓存当前对象的get set方法
            this.cacheGetAndSetMethods(beanClass);
            assembleController(beanName, controller, beanClass);
            //为与sparrow mvc解耦，这里引不到handlerInterceptor接口
            if (Boolean.TRUE.toString().equalsIgnoreCase(interceptor)) {
                container = CONTAINER.INTERCEPTOR.toString().toUpperCase();
            }
            if (!StringUtility.isNullOrEmpty(remote)) {
                container = remote.toUpperCase();
            }
            if (container != null) {
                if (!this.typeBeanFactory.containsKey(container)) {
                    this.typeBeanFactory.put(container, new HashMap<String, Object>(64));
                }
                this.typeBeanFactory.get(container).put(beanName, instance);
            }
            this.beanFactoryCache.put(beanName, instance);
            return instance;
        } catch (Exception e) {
            logger.error(beanName, e);
            return null;
        }
    }
}
