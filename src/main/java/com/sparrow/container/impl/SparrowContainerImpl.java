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

import com.sparrow.cg.MethodAccessor;
import com.sparrow.constant.CONFIG;
import com.sparrow.constant.CONSTANT;
import com.sparrow.constant.SYS_OBJECT_NAME;
import com.sparrow.constant.magic.SYMBOL;
import com.sparrow.container.Container;
import com.sparrow.container.ContainerAware;
import com.sparrow.core.TypeConverter;
import com.sparrow.enums.CONTAINER;
import com.sparrow.protocol.Result;
import com.sparrow.support.Initializer;
import com.sparrow.support.LoginParser;
import com.sparrow.support.LoginDialog;
import com.sparrow.utility.Config;
import com.sparrow.utility.StringUtility;
import com.sparrow.utility.Xml;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * @author harry
 */
public class SparrowContainerImpl extends DocumentParser implements Container {

    public SparrowContainerImpl(String xmlName, String systemConfigPath) {
        this.xmlName = xmlName;
        this.systemConfigPath = systemConfigPath;
    }

    public SparrowContainerImpl(String xmlName) {
        this(xmlName, "/system_config.properties");
    }

    public SparrowContainerImpl() {
        this("/beans.xml", "/system_config.properties");
    }

    private void parseElement(Element element) throws Exception {
        // 如果没有依赖对象的单独节点
        String tagName = element.getTagName().trim();
        if (BEANS.equalsIgnoreCase(tagName)) {
            this.parseChildElement(element.getChildNodes());
            return;
        }
        if (BEAN.equalsIgnoreCase(tagName)) {
            // bean名
            String beanName = element.getAttribute(NAME).trim();
            Object instance = this.parseBean(element, beanName);
            NodeList nodeList = element.getChildNodes();
            // 如果子节点>0则先加载子节点对象
            if (nodeList.getLength() > 0) {
                parseChildElement(nodeList);
            }
            if (instance instanceof ContainerAware) {
                ((ContainerAware) instance).aware(this, beanName);
            }
            return;
        }
        if (PROPERTY.equalsIgnoreCase(tagName)) {
            this.parseProperty(element);
            return;
        }
        //-<import resource="repository.xml"/>-->
        if (IMPORT.equalsIgnoreCase(tagName)) {
            String resource = element.getAttribute("resource");
            if (!resource.startsWith(SYMBOL.SLASH)) {
                resource = SYMBOL.SLASH + resource;
            }
            logger.info("-------------init bean " + resource + " ...---------------------------");
            Document doc = Xml.getXmlDocumentByPath(resource,
                    DTD_FILE_NAME);
            if (doc != null) {
                this.parseElement(doc.getDocumentElement());
            }
        }
    }

    private void parseChildElement(NodeList nodeList) throws Exception {
        Node n;
        for (int i = 0; i < nodeList.getLength(); i++) {
            n = nodeList.item(i);
            if (n.getNodeType() != 1) {
                continue;
            }
            Element e = (Element) n;
            this.parseElement(e);
        }
    }

    @Override
    public MethodAccessor getProxyBean(Class<?> clazz) {
        return this.proxyBeanCache.get(clazz.getSimpleName());
    }

    @Override
    public List<TypeConverter> getFieldList(Class clazz) {
        String clazzName = clazz.getSimpleName();
        return fieldCache.get(clazzName);
    }

    @Override
    public Map<String, Method> getControllerMethod(String clazzName) {
        return this.controllerMethodCache.get(clazzName);
    }


    @Override
    public Map<String, Object> getAllBean() {
        return this.beanFactoryCache;
    }

    @Override
    public void init() {
        this.init(null, null);
    }

    @Override
    public void init(String xmlName, String systemConfigPath) {
        if (!StringUtility.isNullOrEmpty(xmlName)) {
            this.xmlName = xmlName;
        }
        if (!StringUtility.isNullOrEmpty(systemConfigPath)) {
            this.systemConfigPath = systemConfigPath;
        }
        logger.info("-----------------Ioc container init ....-------------------");
        try {
            logger.info("-------------system config file init ...-------------------");
            initSystemConfig();

            logger.info("-------------init bean ...---------------------------");
            Document doc = Xml.getXmlDocumentByPath(this.xmlName, DTD_FILE_NAME);
            if (doc != null) {
                this.parseElement(doc.getDocumentElement());
            }
            logger.info("-------------init initializer ...--------------------------");
            Initializer initializer = this.getBean(
                    SYS_OBJECT_NAME.INITIALIZER);

            if (initializer != null) {
                initializer.init(this);
            }
            logger.info("-----------------Ioc container init success...-------------------");
        } catch (Exception e) {
            logger.error("ioc init error", e);
        } finally {
            this.cacheBeanDefinition("result", Result.class);
            this.cacheBeanDefinition("login", LoginParser.class);
            this.cacheBeanDefinition("loginDialog", LoginDialog.class);
        }
    }

    private void initSystemConfig() throws IOException {
        if (StringUtility.isNullOrEmpty(this.systemConfigPath)) {
            return;
        }
        Config.initSystem(this.systemConfigPath);
        String internationalization = Config
                .getValue(CONFIG.INTERNATIONALIZATION);

        if (StringUtility.isNullOrEmpty(internationalization)) {
            internationalization = Config
                    .getValue(CONFIG.LANGUAGE);
        }
        if (StringUtility.isNullOrEmpty(internationalization)) {
            internationalization = CONSTANT.DEFAULT_LANGUAGE;
        }
        String[] internationalizationArray = internationalization
                .split(SYMBOL.COMMA);
        for (String i18n : internationalizationArray) {
            Config.initInternationalization(i18n);
        }
    }

    @Override
    public Map<String, Object> getBeanMap(CONTAINER container) {
        return this.typeBeanFactory.get(container.toString());
    }
}