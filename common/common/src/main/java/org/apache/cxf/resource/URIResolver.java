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
package org.apache.cxf.resource;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.common.util.Base64Utility;
import org.apache.cxf.common.util.StringUtils;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.helpers.LoadingByteArrayOutputStream;

/**
 * Resolves a File, classpath resource, or URL according to the follow rules:
 * <ul>
 * <li>Check to see if a file exists, relative to the base URI.</li>
 * <li>If the file doesn't exist, check the classpath</li>
 * <li>If the classpath doesn't exist, try to create URL from the URI.</li>
 * </ul>
 * 
 * @author Dan Diephouse
 */
public class URIResolver {
    private Map<String, LoadingByteArrayOutputStream> cache
        = new HashMap<String, LoadingByteArrayOutputStream>();
    private File file;
    private URI uri;
    private URL url;
    private InputStream is;
    private Class calling;

    public URIResolver() {
    }

    public URIResolver(String path) throws IOException {
        this("", path);
    }

    public URIResolver(String baseUriStr, String uriStr) throws IOException {
        this(baseUriStr, uriStr, null);
    }
    
    public URIResolver(String baseUriStr, String uriStr, Class calling) throws IOException {
        this.calling = (calling != null) ? calling : getClass();
        if (uriStr.startsWith("classpath:")) {
            tryClasspath(uriStr);
        } else if (baseUriStr != null 
            && (baseUriStr.startsWith("jar:") 
                || baseUriStr.startsWith("zip:")
                || baseUriStr.startsWith("wsjar:"))) {
            tryArchive(baseUriStr, uriStr);
        } else if (uriStr.startsWith("jar:") 
            || uriStr.startsWith("zip:")
            || uriStr.startsWith("wsjar:")) {
            tryArchive(uriStr);
        } else {
            tryFileSystem(baseUriStr, uriStr);
        }
    }

    public void unresolve() {
        this.file = null;
        this.uri = null;
        this.is = null;
    }
    
    public void resolve(String baseUriStr, String uriStr, Class callingCls) throws IOException {
        this.calling = (callingCls != null) ? callingCls : getClass();
        this.file = null;
        this.uri = null;

        this.is = null;

        if (uriStr.startsWith("classpath:")) {
            tryClasspath(uriStr);
        } else if (baseUriStr != null 
            && (baseUriStr.startsWith("jar:") 
                || baseUriStr.startsWith("zip:")
                || baseUriStr.startsWith("wsjar:"))) {
            tryArchive(baseUriStr, uriStr);
        } else if (uriStr.startsWith("jar:") 
            || uriStr.startsWith("zip:")
            || uriStr.startsWith("wsjar:")) {
            tryArchive(uriStr);
        } else {
            tryFileSystem(baseUriStr, uriStr);
        }
    }

    
    
    private void tryFileSystem(String baseUriStr, String uriStr) throws IOException, MalformedURLException {
        try {
            URI relative;
            File uriFile = new File(uriStr);
            uriFile = new File(uriFile.getAbsolutePath());

            if (uriFile.exists()) {
                relative = uriFile.toURI();
            } else {
                relative = new URI(uriStr.replaceAll(" ", "%20"));
            }
            
            if (relative.isAbsolute()) {
                uri = relative;
                url = relative.toURL();

                try {
                    HttpURLConnection huc = (HttpURLConnection)url.openConnection();

                    String host = System.getProperty("http.proxyHost");
                    if (host != null) {
                        //comment out unused port to pass pmd check
                        /*String ports = System.getProperty("http.proxyPort");
                        int port = 80;
                        if (ports != null) {
                            port = Integer.parseInt(ports);
                        }*/

                        String username = System.getProperty("http.proxy.user");
                        String password = System.getProperty("http.proxy.password");

                        if (username != null && password != null) {
                            String encoded = Base64Utility.encode((username + ":" + password).getBytes());
                            huc.setRequestProperty("Proxy-Authorization", "Basic " + encoded);
                        }
                    }
                    is =  huc.getInputStream();
                } catch (ClassCastException ex) {
                    is = url.openStream();
                }
            } else if (!StringUtils.isEmpty(baseUriStr)) {
                URI base;
                File baseFile = new File(baseUriStr);

                if (!baseFile.exists() && baseUriStr.startsWith("file:/")) {
                    baseFile = new File(baseUriStr.substring(6));
                }

                if (baseFile.exists()) {
                    base = baseFile.toURI();
                } else {
                    base = new URI(baseUriStr);
                }
                
                base = base.resolve(relative);
                if (base.isAbsolute() && "file".equalsIgnoreCase(base.getScheme())) {
                    try {
                        baseFile = new File(base);
                        if (baseFile.exists()) {
                            is = base.toURL().openStream();
                            uri = base;
                        } else {
                            tryClasspath(base.toString().startsWith("file:") 
                                         ? base.toString().substring(5) : base.toString());
                        }
                    } catch (Throwable th) {
                        tryClasspath(base.toString().startsWith("file:") 
                                     ? base.toString().substring(5) : base.toString());
                    }
                } else {
                    tryClasspath(base.toString().startsWith("file:") 
                                 ? base.toString().substring(5) : base.toString());
                }
            } else {
                tryClasspath(uriStr.startsWith("file:") 
                             ? uriStr.substring(5) : uriStr);
            }
        } catch (URISyntaxException e) {
            // do nothing
        }

        if (uri != null && "file".equals(uri.getScheme())) {
            try {
                file = new File(uri);
            } catch (IllegalArgumentException iae) {
                file = new File(uri.toURL().getPath());
                if (!file.exists()) {
                    file = null;
                }
            }
        }

        if (is == null && file != null && file.exists()) {
            uri = file.toURI();
            try {
                is = new FileInputStream(file);
            } catch (FileNotFoundException e) {
                throw new RuntimeException("File was deleted! " + uriStr, e);
            }
            url = file.toURI().toURL();
        } else if (is == null) {
            tryClasspath(uriStr);
        }
    }
    
    private void tryArchive(String baseStr, String uriStr) throws IOException {
        int i = baseStr.indexOf('!');
        if (i == -1) {
            tryFileSystem(baseStr, uriStr);
        }

        String archiveBase = baseStr.substring(0, i + 1);
        String archiveEntry = baseStr.substring(i + 1);
        try {
            URI u = new URI(archiveEntry).resolve(uriStr);

            tryArchive(archiveBase + u.toString());

            if (is != null) {
                if (u.isAbsolute()) {
                    url = u.toURL();
                }
                return;
            }
        } catch (URISyntaxException e) {
            // do nothing
        }
        
        tryFileSystem("", uriStr);
    }
    
    private void tryArchive(String uriStr) throws IOException {
        int i = uriStr.indexOf('!');
        if (i == -1) {
            return;
        }

        url = new URL(uriStr);
        try {
            is = url.openStream();
            try {
                uri = url.toURI();
            } catch (URISyntaxException ex) {
                // ignore
            }
        } catch (IOException e) {
            uriStr = uriStr.substring(i + 1);
            tryClasspath(uriStr);
        }
    }
    
    private void tryClasspath(String uriStr) throws IOException {
        if (uriStr.startsWith("classpath:")) {
            uriStr = uriStr.substring(10);
        }
        url = ClassLoaderUtils.getResource(uriStr, calling);
        if (url == null) {
            tryRemote(uriStr);
        } else {
            try {
                uri = url.toURI();
            } catch (URISyntaxException e) {
                // processing the jar:file:/ type value
                String urlStr = url.toString();
                if (urlStr.startsWith("jar:") 
                    || urlStr.startsWith("zip:")
                    || urlStr.startsWith("wsjar:")) {
                    int pos = urlStr.indexOf('!');
                    if (pos != -1) {
                        try {
                            uri = new URI("classpath:" + urlStr.substring(pos + 1));
                        } catch (URISyntaxException ue) {
                            // ignore
                        }
                    }
                }
                
            }
            is = url.openStream();
        }
    }

    private void tryRemote(String uriStr) throws IOException {
        try {
            LoadingByteArrayOutputStream bout = cache.get(uriStr);
            url = new URL(uriStr);
            uri = new URI(url.toString());
            if (bout == null) {
                URLConnection connection = url.openConnection();
                is = connection.getInputStream();
                bout = new LoadingByteArrayOutputStream(1024);
                IOUtils.copy(is, bout);
                is.close();
                cache.put(uriStr, bout);
            }
            is = bout.createInputStream();
        } catch (MalformedURLException e) {
            // do nothing
        } catch (URISyntaxException e) {
            // do nothing
        }
    }

    public URI getURI() {
        return uri;
    }

    public URL getURL() {
        return url;
    }

    public InputStream getInputStream() {
        return is;
    }

    public boolean isFile() {
        if (file != null) {
            return file.exists();
        }
        return false;
    }

    public File getFile() {
        return file;
    }
    
    public boolean isResolved() {
        return is != null;
    }
}
