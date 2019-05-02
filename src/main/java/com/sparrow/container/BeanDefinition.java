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

package com.sparrow.container;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * bean元数定义类
 *
 * @author harry
 * @version 1.0
 */
public class BeanDefinition {
    /**
     * 类名
     */
    private String className;

    /**
     * 类的依赖
     */
    private List<String> relyOnClass = new ArrayList<>(8);


    private List<String> relyProperties=new ArrayList<>(8);


    private String scope;

    private String constructorArg;

    private Boolean controller=false;

    private Boolean interceptor =false;


    public List<String> getRelyOnClass() {
        return relyOnClass;
    }

    public void setRelyOnClass(List<String> relyOnClass) {
        this.relyOnClass = relyOnClass;
    }

    public List<String> getRelyProperties() {
        return relyProperties;
    }

    public void setRelyProperties(List<String> relyProperties) {
        this.relyProperties = relyProperties;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }
}
