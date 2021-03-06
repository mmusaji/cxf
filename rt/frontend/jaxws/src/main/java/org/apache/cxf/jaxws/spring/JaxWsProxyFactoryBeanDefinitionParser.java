/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.cxf.jaxws.spring;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.bus.spring.BusWiringBeanFactoryPostProcessor;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.spring.ClientProxyFactoryBeanDefinitionParser;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class JaxWsProxyFactoryBeanDefinitionParser extends ClientProxyFactoryBeanDefinitionParser {

    @Override
    protected Class getFactoryClass() {
        return JAXWSSpringClientProxyFactoryBean.class;
    }

    @Override
    protected String getSuffix() {
        return ".jaxws-client";
    }

    
    public static class JAXWSSpringClientProxyFactoryBean extends JaxWsProxyFactoryBean
        implements ApplicationContextAware {

        public JAXWSSpringClientProxyFactoryBean() {
            super();
        }
        public JAXWSSpringClientProxyFactoryBean(ClientFactoryBean fact) {
            super(fact);
        }
        
        public void setApplicationContext(ApplicationContext ctx) throws BeansException {
            if (getBus() == null) {
                Bus bus = BusFactory.getThreadDefaultBus();
                BusWiringBeanFactoryPostProcessor.updateBusReferencesInContext(bus, ctx);
                setBus(bus);
            }
        }
    }
}
