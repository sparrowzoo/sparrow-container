//package com.sparrow.container;
//
//import com.sparrow.enums.CONTAINER;
//import com.sparrow.utility.StringUtility;
//import java.util.HashMap;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.w3c.dom.Element;
///**
// * @author by harry
// */
//public class XmlBeanDefinationRegistry extends SimpleBeanDefinationRegistry {
//    private Logger logger= LoggerFactory.getLogger(XmlBeanDefinationRegistry.class);
//    static final String DTD_FILE_NAME = "beanFactory.dtd";
//    static final String NAME = "name";
//    static final String VALUE = "value";
//    static final String REF = "ref";
//    static final String SCOPE = "scope";
//    static final String CLASS_NAME = "class";
//    static final String BEAN = "bean";
//    static final String IMPORT = "import";
//    static final String BEANS = "beans";
//    static final String SINGLETON = "singleton";
//    static final String PROPERTY = "property";
//    static final String CONSTRUCTOR_ARG = "constructor-arg";
//    static final String CONTROLLER = "controller";
//    static final String INTERCEPTOR = "interceptor";
//    static final String REMOTE = "remote";
//    /**
//     * 读xml标签初始化bean 并加入到bean factory 和bean definition factory
//     */
//    void parseBean(Element element, String beanName) {
//        // 是否为单子实例
//        String scope = element.getAttribute(SCOPE);
//        // class名
//        String className = element.getAttribute(CLASS_NAME);
//        //构造参数
//        String constructorArg = element.getAttribute(CONSTRUCTOR_ARG);
//        //controller名
//        String controller = element.getAttribute(CONTROLLER);
//        //拦截器
//        String interceptor = element.getAttribute(INTERCEPTOR);
//        //远程bean
//        String remote = element.getAttribute(REMOTE);
//
//        String container = null;
//
//
//        BeanDefinition
//
//            //缓存当前对象的get set方法
//            this.cacheGetAndSetMethods(beanClass);
//            assembleController(beanName, controller, beanClass);
//            //为与sparrow mvc解耦，这里引不到handlerInterceptor接口
//            if (Boolean.TRUE.toString().equalsIgnoreCase(interceptor)) {
//                container = CONTAINER.INTERCEPTOR.toString().toUpperCase();
//            }
//            if (!StringUtility.isNullOrEmpty(remote)) {
//                container = remote.toUpperCase();
//            }
//            if (container != null) {
//                if (!this.typeBeanFactory.containsKey(container)) {
//                    this.typeBeanFactory.put(container, new HashMap<String, Object>(64));
//                }
//                this.typeBeanFactory.get(container).put(beanName, instance);
//            }
//            this.beanFactoryCache.put(beanName, instance);
//            return instance;
//        } catch (Exception e) {
//            logger.error(beanName, e);
//            return null;
//        }
//}
