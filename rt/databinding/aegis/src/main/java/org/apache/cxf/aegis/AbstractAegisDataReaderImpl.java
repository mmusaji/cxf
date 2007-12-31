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

package org.apache.cxf.aegis;

import java.util.HashMap;
import java.util.Map;

import javax.xml.validation.Schema;

import org.apache.cxf.common.util.SOAPConstants;
import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.interceptor.Fault;

/**
 * Common implementation of the Aegis data readers.
 */
public abstract class AbstractAegisDataReaderImpl implements AegisDataReader {
    protected Map<String, Object> properties;
    protected Schema schema;
    protected AegisContext aegisContext;
    protected Context context;
    
    protected AbstractAegisDataReaderImpl(AegisContext globalContext) {
        aegisContext = globalContext;
        context = new Context(globalContext);
        properties = new HashMap<String, Object>();
    }  
    
    /**
     * Due to the fact that the element data reader borrows this class, we need
     * a constructor that takes an existing context.
     * @param globalContext
     * @param context
     */
    protected AbstractAegisDataReaderImpl(AegisContext globalContext, Context context) {
        aegisContext = globalContext;
        this.context = context;
        properties = new HashMap<String, Object>();
    }  
        
    /** {@inheritDoc}*/
    public void setProperty(String prop, Object value) {
        if (SOAPConstants.MTOM_ENABLED.equals(prop)) {
            if (value instanceof String) {
                context.setMtomEnabled(Boolean.valueOf((String)value));
            } else if (value instanceof Boolean) {
                context.setMtomEnabled((Boolean)value);
            }
        } else if (DataReader.FAULT.equals(prop)) { 
            context.setFault((Fault)value);
        }
    }

    /** {@inheritDoc}*/
    public void setSchema(Schema s) {
    }
    
    public Context getContext() {
        return context;
    }
}
