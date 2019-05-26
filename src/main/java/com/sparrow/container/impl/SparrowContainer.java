package com.sparrow.container.impl;

import com.sparrow.constant.CONFIG;
import com.sparrow.constant.SYS_OBJECT_NAME;
import com.sparrow.container.AbstractContainer;
import com.sparrow.container.BeanDefinition;
import com.sparrow.container.BeanDefinitionParserDelegate;
import com.sparrow.container.BeanDefinitionReader;
import com.sparrow.container.SimpleBeanDefinitionRegistry;
import com.sparrow.container.XmlBeanDefinitionReader;
import com.sparrow.protocol.LoginToken;
import com.sparrow.protocol.Result;
import com.sparrow.protocol.constant.CONSTANT;
import com.sparrow.protocol.constant.magic.SYMBOL;
import com.sparrow.protocol.mvn.HandlerInterceptor;
import com.sparrow.support.Initializer;
import com.sparrow.support.LoginDialog;
import com.sparrow.utility.Config;
import com.sparrow.utility.StringUtility;
import java.io.IOException;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author by harry
 */
public class SparrowContainer extends AbstractContainer {
    private String xmlName;

    private String systemConfigPath;

    private static Logger logger= LoggerFactory.getLogger(SparrowContainer.class);

    @Override public void init() {
        this.init("/beans.xml", "/system_config.properties");
    }

    @Override public void init(String xmlName, String systemConfigPath) {
        if (!StringUtility.isNullOrEmpty(xmlName)) {
            this.xmlName = xmlName;
        }
        if (!StringUtility.isNullOrEmpty(systemConfigPath)) {
            this.systemConfigPath = systemConfigPath;
        }
        logger.info("----------------- container init ....-------------------");
        try {
            logger.info("-------------system config file init ...-------------------");
            initSystemConfig();
            logger.info("-------------init bean ...---------------------------");
            SimpleBeanDefinitionRegistry registry=new SimpleBeanDefinitionRegistry();
            BeanDefinitionParserDelegate delegate=new BeanDefinitionParserDelegate();
            BeanDefinitionReader definitionReader=new XmlBeanDefinitionReader(registry,delegate);
            definitionReader.loadBeanDefinitions(this.xmlName);

            this.beanDefinitionRegistry=registry;

            Iterator<String> iterator= registry.keyIterator();
            while (iterator.hasNext()){
                String beanName=iterator.next();
                BeanDefinition bd=registry.getObject(beanName);
                this.initMethod(bd);
                if(bd.isSingleton()) {
                    Object o = this.instance(bd);
                    this.singletonRegistry.pubObject(beanName,o);
                    if(bd.isController()){
                        this.assembleController(beanName,o);
                    }
                    if(bd.isInterceptor()){
                        this.interceptorRegistry.pubObject(beanName,(HandlerInterceptor) o);
                    }
                }
                else
                {
                    Class clazz=Class.forName(bd.getBeanClassName());
                    this.initProxyBean(clazz);
                }
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
            this.initProxyBean(Result.class);
            this.initProxyBean(LoginToken.class);
            this.initProxyBean(LoginDialog.class);
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
}
