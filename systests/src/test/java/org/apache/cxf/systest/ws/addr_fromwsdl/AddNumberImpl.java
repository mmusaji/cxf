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

package org.apache.cxf.systest.ws.addr_fromwsdl;

import javax.jws.WebService;
import javax.xml.ws.soap.Addressing;
import org.apache.cxf.systest.ws.addr_feature.AddNumbersPortType;

// Jax-WS 2.1 WS-Addressing FromWsdl

@Addressing
@WebService(serviceName = "AddNumbersService",
            targetNamespace = "http://apache.org/cxf/systest/ws/addr_feature/")
public class AddNumberImpl implements AddNumbersPortType {
    public int addNumbers(int number1, int number2) {
        return number1 + number2;
    }

    public int addNumbers2(int number1, int number2) {
        return number1 + number2;
    }

    public int addNumbers3(int number1, int number2) {
        return number1 + number2;
    }
}
