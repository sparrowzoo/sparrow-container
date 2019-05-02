package com.sparrow.container;

/**
 * @author by harry
 */
public class SimpleSingletonRegistry implements FactoryBean<Object> {

    @Override public void pubObject(String name, Object o) {

    }

    @Override public Object getObject(String name) {
        return null;
    }

    @Override public Class<?> getObjectType() {
        return null;
    }

    @Override public void removeObject(String name) {

    }
}
