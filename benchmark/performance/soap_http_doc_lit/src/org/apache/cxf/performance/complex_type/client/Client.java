/*
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
package org.apache.cxf.performance.complex_type.client;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.namespace.QName;

import org.apache.cxf.pat.internal.TestCaseBase;
import org.apache.cxf.pat.internal.TestResult;
import org.apache.cxf.cxf.performance.DocPortType;
import org.apache.cxf.cxf.performance.DocPortTypeWrapped;
import org.apache.cxf.cxf.performance.RPCPortType;
import org.apache.cxf.cxf.performance.PerfService;
import org.apache.cxf.cxf.performance.types.ColourEnum;
import org.apache.cxf.cxf.performance.types.NestedComplexType;
import org.apache.cxf.cxf.performance.types.NestedComplexTypeSeq;
import org.apache.cxf.cxf.performance.types.SimpleStruct;
//import org.apache.cxf.cxf.performance.server.Server;
import org.apache.cxf.performance.complex_type.server.Server;


public final class Client extends TestCaseBase<DocPortType> {
    private static final QName SERVICE_NAME = new QName(
                                                       "http://cxf.apache.org/cxf/performance",
                                                       "PerfService");          
    private static final QName PORT_NAME = new QName(
                                                    "http://cxf.apache.org/cxf/performance",
                                                    "DocPortType");
    private PerfService cs;
    private final NestedComplexTypeSeq complexTypeSeq = new NestedComplexTypeSeq();
    private int opid;

    private byte[] inputBase64;
    private String inputString = new String();

    private final int asciiCount = 1 * 1024;

    public Client(String[] args, boolean warmup) {
        super("Base TestCase", args, warmup);
        serviceName = "PerfService";
        portName = "PerfService";
        operationName = "echoComplexTypeDoc";
        wsdlNameSpace = "http://cxf.apache.org/cxf/performance";
        amount = 30;
        packetSize = 1;
    }

    public void processArgs() {
        super.processArgs();
        if (getOperationName().equals("echoStringDoc")) {
            opid = 0;
        } else if (getOperationName().equals("echoBase64Doc")) {
            opid = 1;
        } else if (getOperationName().equals("echoComplexTypeDoc")) {
            opid = 2;
        } else {
            System.out.println("Invalid operation: " + getOperationName());
        }
    }

    public static void main(String args[]) throws Exception {

        int threadIdx = -1;
        for (int x = 0; x < args.length; x++) {
            if ("-Threads".equals(args[x])) {
                threadIdx = x + 1;
            }
        }

        List<String> threadList = new ArrayList<String>();
        if (threadIdx != -1) {
            String threads[] = args[threadIdx].split(",");
            for (String s : threads) {
                if (s.indexOf("-") != -1) {
                    String s1 = s.substring(0, s.indexOf("-"));
                    String s2 = s.substring(s.indexOf("-") + 1);
                    int i1 = Integer.parseInt(s1);
                    int i2 = Integer.parseInt(s2);
                    for (int x = i1; x <= i2; x++) {
                        threadList.add(Integer.toString(x));
                    }                
                } else {
                    threadList.add(s);
                } 
            }
        } else {
            threadList.add("1");
        }
        System.out.println(threadList);
        boolean first = true;
        for (String numThreads: threadList) {
            if (threadIdx != -1) {
                args[threadIdx] = numThreads;
            }
            System.out.println(Arrays.asList(args));
            Client client = new Client(args, first);
            first = false;
            client.initialize(); 


            client.run();

            List results = client.getTestResults();
            TestResult testResult = null;

            double rt = 0.0;
            double tp = 0.0;
            for (Iterator iter = results.iterator(); iter.hasNext();) {
                testResult = (TestResult)iter.next();
                System.out.println("Throughput " + testResult.getThroughput());
                System.out.println("AVG Response Time " + testResult.getAvgResponseTime());
                rt += testResult.getAvgResponseTime();
                tp += testResult.getThroughput();
            }
            rt *= 1000;
            rt /= (double)results.size();

            System.out.println("Total(" + numThreads + "):  " + tp + " tps     " + rt + " ms");

            System.out.println();
            System.out.println();
            System.out.println();
        }
        System.out.println("cxf client is going to shutdown!");
        System.exit(0);
    }

    private SimpleStruct getSimpleStruct() throws DatatypeConfigurationException {
        SimpleStruct ss = new SimpleStruct();
        ss.setVarFloat(Float.MAX_VALUE);
        ss.setVarShort(Short.MAX_VALUE);
        ss.setVarByte(Byte.MAX_VALUE);
        ss.setVarDecimal(new BigDecimal("3.1415926"));
        ss.setVarDouble(Double.MAX_VALUE);
        ss.setVarString("1234567890!@#$%^&*()abcdefghijk");
        ss.setVarAttrString("1234567890!@#$%^&*()abcdefghijk");
        ss.setVarDateTime(DatatypeFactory.newInstance().newXMLGregorianCalendar(2005, 12, 3, 0, 0, 9, 0, 0));
        return ss;
    }


    private NestedComplexType createComplexType() {
        NestedComplexType complexType = new NestedComplexType();
        complexType.setVarString("#12345ABc");
        complexType.setVarUByte((short)255);
        complexType.setVarUnsignedLong(new BigInteger("13691056728"));
        complexType.setVarFloat(Float.MAX_VALUE);
        complexType.setVarQName(new QName("return", "return"));
        try {
            complexType.setVarStruct(getSimpleStruct());
        } catch (DatatypeConfigurationException e) {
            e.printStackTrace();
        }

        complexType.setVarEnum(ColourEnum.RED);
        byte[] binary = new byte[256 * packetSize];
        for (int idx = 0; idx < 1 * packetSize; idx++) {
            for (int jdx = 0; jdx < 256; jdx++) {
                binary[idx * 256 + jdx] = (byte)(jdx - 128);
            }
        }
        complexType.setVarHexBinary(binary);
        complexType.setVarBase64Binary(binary);
        return complexType;
    }

    public void initTestData() {
        //for (int i = 0; i < packetSize; i++) {
        NestedComplexType ct = createComplexType();
        for (int i = 0; i < 1; i++) {
            complexTypeSeq.getItem().add(ct);
        }            
        // init String and Binary
        String temp = "abcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+?><[]/0123456789";
        inputBase64 = new byte[1024 * packetSize];
        for (int idx = 0; idx < 4 * packetSize; idx++) {
            for (int jdx = 0; jdx < 256; jdx++) {
                inputBase64[idx * 256 + jdx] = (byte)(jdx - 128);
            }
        }

        for (int i = 0; i < asciiCount / temp.length() * packetSize; i++) {
            inputString = inputString + temp;
        }

    }

    public void doJob(DocPortType port) {
        try {
            switch (opid) {
            case 0:
                port.echoStringDoc(inputString);
                break;
            case 1:
                port.echoBase64Doc(inputBase64);
                break;
            case 2:
                port.echoComplexTypeDoc(complexTypeSeq);
                break;
            default:
                port.echoComplexTypeDoc(complexTypeSeq);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DocPortType getPort() {
        try {
            URL wsdl = null;
            if ((wsdlPath.startsWith("file://")) || (wsdlPath.startsWith("http://"))) {
                wsdl = new URL(wsdlPath);
            } else {
                wsdl = new URL("file://" + wsdlPath);
            }
            cs = new PerfService(wsdl, SERVICE_NAME);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return cs.getSoapHttpDocLitPort();
    }

    public void printUsage() {
        System.out.println("Syntax is: Client [-WSDL wsdllocation] [-PacketSize packetnumber] ");
    }
}
