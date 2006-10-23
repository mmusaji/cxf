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
package org.apache.cxf.binding.http;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;

public class ContentTypeOutInterceptor extends AbstractPhaseInterceptor<Message> {

    public ContentTypeOutInterceptor() {
        super();
        setPhase(Phase.PREPARE_SEND);
    }

    @SuppressWarnings("unchecked")
    public void handleMessage(Message message) throws Fault {
        Map<String, List<String>> headers = (Map<String, List<String>>)message.get(Message.PROTOCOL_HEADERS);
        if (headers == null) {
            headers = new HashMap<String, List<String>>();
            message.put(Message.PROTOCOL_HEADERS, headers);
        }
        List<String> contentType = new ArrayList<String>();
        contentType.add("application/xml");
        headers.put("Content-Type", contentType);
    }

}
