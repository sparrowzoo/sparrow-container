package com.sparrow.container;

import java.util.HashMap;
import java.util.Map;

/**
 * @author: zhanglizhi@kanzhun.com
 * @date: 2019-05-02 19:13
 * @description:
 */
public class SimpleBeanDefinitionRegistry implements FactoryBean<BeanDefinition> {
    private Map<String, BeanDefinition> map = new HashMap<>(256);

    @Override
    public void pubObject(String name, BeanDefinition o) {
        map.put(name, o);
    }

    @Override
    public BeanDefinition getObject(String name) {
        return map.get(name);
    }

    @Override
    public Class<?> getObjectType() {
        return BeanDefinition.class;
    }

    @Override
    public void removeObject(String name) {
        this.map.remove(name);
    }
}
