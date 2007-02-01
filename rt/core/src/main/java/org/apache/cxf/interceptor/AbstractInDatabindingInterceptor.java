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

package org.apache.cxf.interceptor;

import java.util.Collection;
import java.util.Iterator;
import java.util.ResourceBundle;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;

import org.w3c.dom.Node;

import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.databinding.DataReader;
import org.apache.cxf.databinding.DataReaderFactory;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingMessageInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.MessageInfo;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.OperationInfo;
import org.apache.cxf.service.model.ServiceModelUtil;
import org.apache.cxf.staxutils.DepthXMLStreamReader;
import org.apache.cxf.wsdl.EndpointReferenceUtils;

public abstract class AbstractInDatabindingInterceptor extends AbstractPhaseInterceptor<Message> {

    private static final QName XSD_ANY = new QName("http://www.w3.org/2001/XMLSchema", "anyType", "xsd");

    private static final ResourceBundle BUNDLE = BundleUtils
        .getBundle(AbstractInDatabindingInterceptor.class);

    protected boolean isRequestor(Message message) {
        return Boolean.TRUE.equals(message.get(Message.REQUESTOR_ROLE));
    }

    protected DataReader getDataReader(Message message, Class<?> input) {
        Service service = ServiceModelUtil.getService(message.getExchange());
        DataReaderFactory factory = service.getDataBinding().getDataReaderFactory();
        setSchemaInMessage(service, message);
        DataReader dataReader = null;
        for (Class<?> cls : factory.getSupportedFormats()) {
            if (cls == input) {
                dataReader = factory.createReader(input);
                break;
            }
        }
        if (dataReader == null) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("NO_DATAREADER", 
                                                                   BUNDLE, service.getName()));
        }
        return dataReader;
    }

    protected DataReader<Message> getMessageDataReader(Message message) {
        Service service = ServiceModelUtil.getService(message.getExchange());
        DataReaderFactory factory = service.getDataBinding().getDataReaderFactory();
        setSchemaInMessage(service, message);
        DataReader<Message> dataReader = null;
        for (Class<?> cls : factory.getSupportedFormats()) {
            if (cls == Message.class) {
                dataReader = factory.createReader(Message.class);
                break;
            }
        }
        if (dataReader == null) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("NO_DATAREADER", 
                                                                   BUNDLE, service.getName()));
        }
        return dataReader;
    }

    protected DataReader<XMLStreamReader> getDataReader(Message message) {
        Service service = ServiceModelUtil.getService(message.getExchange());
        DataReaderFactory factory = service.getDataBinding().getDataReaderFactory();
        setSchemaInMessage(service, message);
        DataReader<XMLStreamReader> dataReader = null;
        for (Class<?> cls : factory.getSupportedFormats()) {
            if (cls == XMLStreamReader.class) {
                dataReader = factory.createReader(XMLStreamReader.class);
                break;
            }
        }
        if (dataReader == null) {
            throw new Fault(new org.apache.cxf.common.i18n.Message("NO_DATAREADER", 
                                                                   BUNDLE, service.getName()));
        }
        return dataReader;
    }

    protected DataReader<Node> getNodeDataReader(Message message) {
        Service service = ServiceModelUtil.getService(message.getExchange());
        DataReaderFactory factory = service.getDataBinding().getDataReaderFactory();
        setSchemaInMessage(service, message);
        DataReader<Node> dataReader = null;
        for (Class<?> cls : factory.getSupportedFormats()) {
            if (cls == Node.class) {
                dataReader = factory.createReader(Node.class);
                break;
            }
        }
        if (dataReader == null) {
            throw new Fault(
                            new org.apache.cxf.common.i18n.Message("NO_DATAREADER", BUNDLE, 
                                                                   service.getName()));
        }
        return dataReader;
    }

    private void setSchemaInMessage(Service service, Message message) {
        if (message.getContextualProperty(Message.SCHEMA_VALIDATION_ENABLED) != null 
                && Boolean.TRUE.equals(message.getContextualProperty(Message.SCHEMA_VALIDATION_ENABLED))) {
            Schema schema = EndpointReferenceUtils.getSchema(service.getServiceInfo());
            service.getDataBinding().getDataReaderFactory().setSchema(schema);
        }
    }
    
    protected DepthXMLStreamReader getXMLStreamReader(Message message) {
        XMLStreamReader xr = message.getContent(XMLStreamReader.class);
        if (xr instanceof DepthXMLStreamReader) {
            return (DepthXMLStreamReader) xr;
        }
        DepthXMLStreamReader dr = new DepthXMLStreamReader(xr);
        message.setContent(XMLStreamReader.class, dr);
        return dr;
    }

    /**
     * Find the next possible message part in the message. If an operation in
     * the list of operations is no longer a viable match, it will be removed
     * from the Collection.
     * 
     * @param exchange
     * @param operations
     * @param name
     * @param client
     * @param index
     * @return
     */
    protected MessagePartInfo findMessagePart(Exchange exchange, Collection<OperationInfo> operations,
                                              QName name, boolean client, int index) {
        Endpoint ep = exchange.get(Endpoint.class);
        MessagePartInfo lastChoice = null;
        for (Iterator<OperationInfo> itr = operations.iterator(); itr.hasNext();) {
            OperationInfo op = itr.next();

            BindingOperationInfo boi = ep.getEndpointInfo().getBinding().getOperation(op);
            if (boi == null) {
                continue;
            }
            BindingMessageInfo msgInfo = null;
            if (client) {
                msgInfo = boi.getOutput();
            } else {
                msgInfo = boi.getInput();
            }

            if (msgInfo == null) {
                itr.remove();
                continue;
            }
            
            Collection bodyParts = msgInfo.getMessageParts();
            if (bodyParts.size() == 0 || bodyParts.size() <= index) {
                itr.remove();
                continue;
            }

            MessagePartInfo p = (MessagePartInfo)msgInfo.getMessageParts().get(index);
            if (name.equals(p.getConcreteName())) {
                exchange.put(BindingOperationInfo.class, boi);
                exchange.put(OperationInfo.class, boi.getOperationInfo());
                exchange.setOneWay(op.isOneWay());
                return p;
            }

            if (XSD_ANY.equals(p.getTypeQName())) {
                lastChoice = p;
            } else {
                itr.remove();
            }
        }
        return lastChoice;
    }    

    /**
     * Returns a BindingOperationInfo if the operation is indentified as 
     * a wrapped method,  return null if it is not a wrapped method 
     * (i.e., it is a bare method)
     * 
     * @param exchange
     * @param name
     * @param client
     * @return
     */
    protected BindingOperationInfo getBindingOperationInfoForWrapped(Exchange exchange, QName name,
                                                                     boolean client) {
        String local = name.getLocalPart();
        if (client && local.endsWith("Response")) {
            local = local.substring(0, local.length() - 8);
        }

        // TODO: Allow overridden methods.
        BindingOperationInfo operation = ServiceModelUtil.getOperation(exchange, local);
        if (operation != null && operation.isUnwrappedCapable()) {
            return operation;
        }

        return null;
    }
    
    protected MessageInfo getMessageInfo(Message message, BindingOperationInfo operation, Exchange ex) {
        return getMessageInfo(message, operation, isRequestor(message));
    }
    
    protected MessageInfo getMessageInfo(Message message, BindingOperationInfo operation, boolean requestor) {
        MessageInfo msgInfo;
        OperationInfo intfOp = operation.getOperationInfo();
        if (requestor) {
            msgInfo = intfOp.getOutput();
            message.put(MessageInfo.class, intfOp.getOutput());
        } else {
            msgInfo = intfOp.getInput();
            message.put(MessageInfo.class, intfOp.getInput());
        }
        return msgInfo;
    }
}
