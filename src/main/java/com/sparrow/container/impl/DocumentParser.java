package com.sparrow.container.impl;

import com.sparrow.constant.magic.SYMBOL;
import com.sparrow.container.BeanDefinition;
import com.sparrow.container.ContainerAware;
import com.sparrow.container.ParseContext;
import com.sparrow.enums.CONTAINER;
import com.sparrow.utility.StringUtility;
import com.sparrow.utility.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.HashMap;

/**
 * Created by haryy on 2018/1/19.
 */
public class DocumentParser extends ParseContext{

    protected void parseProperty(Element element) throws Exception {
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
     * 读xml标签初始化bean 并加入到bean factory 和bean defination facotory
     */
    protected Object parseBean(Element element, String beanName) {
        // 是否为单子实例
        String scope = element.getAttribute(SCOPE);
        // class名
        String className = element.getAttribute(CLASS_NAME);
        //构造参数
        String constructorArg = element.getAttribute("constructor-arg");
        //controller名
        String controller = element.getAttribute("controller");
        //拦截器
        String interceptor = element.getAttribute("interceptor");
        //远程bean
        String remote = element.getAttribute("remote");

        String container = null;

        Class<?> beanClass;
        try {
            beanClass = Class.forName(className);
        } catch (Exception e) {
            logger.error("bean name error :" + beanName, e);
            return null;
        }
        // 如果是单例对象
        if (!StringUtility.isNullOrEmpty(scope) && !SINGLETON.equalsIgnoreCase(scope)) {
            // 如果不是单例则缓存该类的元数据
            this.cacheBeanDefinition(beanName, beanClass);
            return null;
        }
        try {
            Object instance = getInstance(constructorArg, beanClass);
            if (instance == null) {
                return null;
            }
            assembleController(beanName, controller, beanClass);
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
