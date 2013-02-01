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
package org.apache.cxf.io;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.easymock.EasyMock;
import org.easymock.IMocksControl;

import org.junit.Assert;
import org.junit.Test;

public abstract class CachedStreamTestBase extends Assert {
    protected abstract void reloadDefaultProperties();
    protected abstract Object createCache();
    protected abstract Object createCache(long threshold);
    protected abstract Object createCache(long threshold, String transformation);
    protected abstract String getResetOutValue(String result, Object cache) throws IOException;
    protected abstract File getTmpFile(String result, Object cache) throws IOException;
    protected abstract Object getInputStreamObject(Object cache) throws IOException;
    protected abstract String readFromStreamObject(Object cache) throws IOException;
    
    @Test
    public void testResetOut() throws IOException {
        String result = initTestData(16);
        Object cache = createCache();
        String test = getResetOutValue(result, cache);
        assertEquals("The test stream content isn't same ", test , result);
        close(cache);
    }
    
    @Test
    public void testDeleteTmpFile() throws IOException {
        Object cache = createCache();
        //ensure output data size larger then 64k which will generate tmp file
        String result = initTestData(65);
        File tempFile = getTmpFile(result, cache);
        assertNotNull(tempFile);
        //assert tmp file is generated
        assertTrue(tempFile.exists());
        close(cache);
        //assert tmp file is deleted after close the CachedOutputStream
        assertFalse(tempFile.exists());
    }

    @Test
    public void testDeleteTmpFile2() throws IOException {
        Object cache = createCache();
        //ensure output data size larger then 64k which will generate tmp file
        String result = initTestData(65);
        File tempFile = getTmpFile(result, cache);
        assertNotNull(tempFile);
        //assert tmp file is generated
        assertTrue(tempFile.exists());
        Object in = getInputStreamObject(cache);
        close(cache);
        //assert tmp file is not deleted when the input stream is open
        assertTrue(tempFile.exists());
        close(in);
        //assert tmp file is deleted after the input stream is closed
        assertFalse(tempFile.exists());
    }
    
    @Test
    public void testEncryptAndDecryptWithDeleteOnClose() throws IOException {
        // need a 8-bit cipher so that all bytes are flushed when the stream is flushed.
        Object cache = createCache(4, "RC4");
        final String text = "Hello Secret World!";
        File tmpfile = getTmpFile(text, cache);
        assertNotNull(tmpfile);

        final String enctext = readFromStream(new FileInputStream(tmpfile));
        assertFalse("text is not encoded", text.equals(enctext));

        Object fin = getInputStreamObject(cache);

        assertTrue("file is deleted", tmpfile.exists());
        
        final String dectext = readFromStreamObject(fin);
        assertEquals("text is not decoded correctly", text, dectext);

        // the file is deleted when cos is closed while all the associated inputs are closed
        assertTrue("file is deleted", tmpfile.exists());
        close(cache);
        assertFalse("file is not deleted", tmpfile.exists());
    }

    @Test
    public void testEncryptAndDecryptWithDeleteOnInClose() throws IOException {
        // need a 8-bit cipher so that all bytes are flushed when the stream is flushed.
        Object cache = createCache(4, "RC4");
        final String text = "Hello Secret World!";
        File tmpfile = getTmpFile(text, cache);
        assertNotNull(tmpfile);
        
        final String enctext = readFromStream(new FileInputStream(tmpfile));
        assertFalse("text is not encoded", text.equals(enctext));

        Object fin = getInputStreamObject(cache);

        close(cache);
        assertTrue("file is deleted", tmpfile.exists());
        
        // the file is deleted when cos is closed while all the associated inputs are closed
        final String dectext = readFromStreamObject(fin);
        assertEquals("text is not decoded correctly", text, dectext);
        assertFalse("file is not deleted", tmpfile.exists());
    }


    @Test
    public void testUseSysProps() throws Exception {
        String old = System.getProperty("org.apache.cxf.io.CachedOutputStream.Threshold");
        try {
            System.clearProperty("org.apache.cxf.io.CachedOutputStream.Threshold");
            reloadDefaultProperties();
            Object cache = createCache();
            File tmpfile = getTmpFile("Hello World!", cache);
            assertNull("expects no tmp file", tmpfile);
            close(cache);
            
            System.setProperty("org.apache.cxf.io.CachedOutputStream.Threshold", "4");
            reloadDefaultProperties();
            cache = createCache();
            tmpfile = getTmpFile("Hello World!", cache);
            assertNotNull("expects a tmp file", tmpfile);
            assertTrue("expects a tmp file", tmpfile.exists());
            close(cache);
            assertFalse("expects no tmp file", tmpfile.exists());
        } finally {
            if (old != null) {
                System.setProperty("org.apache.cxf.io.CachedOutputStream.Threshold", old);
            }
        }
    }


    @Test
    public void testUseBusProps() throws Exception {
        Bus oldbus = BusFactory.getThreadDefaultBus(false); 
        try {
            Object cache = createCache(64);
            File tmpfile = getTmpFile("Hello World!", cache);
            assertNull("expects no tmp file", tmpfile);
            close(cache);
            
            IMocksControl control = EasyMock.createControl();
            
            Bus b = control.createMock(Bus.class);
            EasyMock.expect(b.getProperty("bus.io.CachedOutputStream.Threshold")).andReturn("4");
            EasyMock.expect(b.getProperty("bus.io.CachedOutputStream.MaxSize")).andReturn(null);
            EasyMock.expect(b.getProperty("bus.io.CachedOutputStream.CipherTransformation")).andReturn(null);
        
            BusFactory.setThreadDefaultBus(b);
            
            control.replay();

            cache = createCache();
            tmpfile = getTmpFile("Hello World!", cache);
            assertNotNull("expects a tmp file", tmpfile);
            assertTrue("expects a tmp file", tmpfile.exists());
            close(cache);
            assertFalse("expects no tmp file", tmpfile.exists());
            
            control.verify();
        } finally {
            BusFactory.setThreadDefaultBus(oldbus);
        }
    }
    
    private static void close(Object obj) throws IOException {
        if (obj instanceof CachedOutputStream) {
            ((CachedOutputStream)obj).close();
        } else if (obj instanceof CachedWriter) {
            ((CachedWriter)obj).close();
        } else if (obj instanceof InputStream) {
            ((InputStream)obj).close();
        } else if (obj instanceof Reader) {
            ((Reader)obj).close();
        }
    }

    protected static String readFromStream(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            byte[] b = new byte[100];
            for (;;) {
                int n = is.read(b, 0, b.length);
                if (n < 0) {
                    break;
                }
                buf.write(b, 0, n);
            }
        } finally {
            is.close();
        }
        return new String(buf.toByteArray(), "UTF-8");
    }
 
    protected static String readFromReader(Reader is) throws IOException {
        StringBuffer buf = new StringBuffer();
        try {
            char[] b = new char[100];
            for (;;) {
                int n = is.read(b, 0, b.length);
                if (n < 0) {
                    break;
                }
                buf.append(b, 0, n);
            }
        } finally {
            is.close();
        }
        return buf.toString();
    }
    
    private static String initTestData(int packetSize) {
        String temp = "abcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+?><[]/0123456789";
        String result = new String();
        for (int i = 0; i <  1024 * packetSize / temp.length(); i++) {
            result = result + temp;
        }
        return result;
    }
}
    
   
