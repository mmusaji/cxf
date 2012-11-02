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

package org.apache.cxf.jaxb;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapters;

/**
 * JAXB reflection utilities.
 */
final class Utils {
    private Utils() {
    }
    
    static XmlAccessType getXmlAccessType(Class<?> cls) {
        XmlAccessorType accessorType = cls.getAnnotation(XmlAccessorType.class);
        if (accessorType == null && cls.getPackage() != null) {
            accessorType = cls.getPackage().getAnnotation(XmlAccessorType.class);
        }
        return accessorType != null
            ? accessorType.value() : XmlAccessType.PUBLIC_MEMBER;
    }

    static Collection<Field> getFields(Class<?> cls, XmlAccessType accessType) {
        if (cls.isAnnotationPresent(XmlTransient.class)) {
            // When placed on a class, it indicates that the class shouldn't be mapped
            // to XML by itself. Properties on such class will be mapped to XML along
            // with its derived classes, as if the class is inlined.
            return Collections.emptySet();
        } else {
            return getFieldsInternal(cls, accessType);
        }
    }

    private static Collection<Field> getFieldsInternal(Class<?> cls, XmlAccessType accessType) {
        Set<Field> fields = new HashSet<Field>();
        Class<?> superClass = cls.getSuperclass();
        if (superClass != null && superClass.isAnnotationPresent(XmlTransient.class)) {
            // only if superClass is @XmlTransient annotated it will be processed recursively
            fields.addAll(getFieldsInternal(superClass, accessType));
        }
        // process current class
        for (Field field : cls.getDeclaredFields()) {
            if (JAXBContextInitializer.isFieldAccepted(field, accessType)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static Collection<Method> getMethods(Class<?> cls, XmlAccessType accessType, boolean acceptSetters) {
        if (cls.isAnnotationPresent(XmlTransient.class)) {
            // When placed on a class, it indicates that the class shouldn't be mapped
            // to XML by itself. Properties on such class will be mapped to XML along
            // with its derived classes, as if the class is inlined.
            return Collections.emptySet();
        } else {
            return getMethodsInternal(cls, accessType, acceptSetters);
        }
    }
    
    private static Collection<Method> getMethodsInternal(Class<?> cls, XmlAccessType accessType,
            boolean acceptSetters) {
        Set<Method> methods = new HashSet<Method>();
        Class<?> superClass = cls.getSuperclass();
        if (superClass != null && superClass.isAnnotationPresent(XmlTransient.class)) {
            // only if superClass is @XmlTransient annotated it will be processed recursively
            methods.addAll(getMethodsInternal(superClass, accessType, acceptSetters));
        }
        // process current class
        for (Method method : cls.getDeclaredMethods()) {
            if (isMethodAccepted(method, accessType, acceptSetters)) {
                methods.add(method);
            }
        }
        return methods;
    }
    
    static Method getMethod(Class<?> cls, XmlAccessType accessType, String methodName,
            Class<?>... paramTypes) {
        for (Method m : getMethods(cls, accessType, true)) {
            if (m.getName().equals(methodName) && Arrays.equals(m.getParameterTypes(), paramTypes)) {
                return m;
            }
        }
        return null;
    }
    
    static Field getField(Class<?> cls, XmlAccessType accessType, String fieldName) {
        for (final Field f : getFields(cls, accessType)) {
            if (f.getName().equals(fieldName)) {
                return f;
            }
        }
        return null;
    }
    
    static Collection<Method> getGetters(Class<?> cls, XmlAccessType accessType) {
        return getMethods(cls, accessType, false);
    }

    static boolean isMethodAccepted(Method method, XmlAccessType accessType, boolean acceptSetters) {
        // ignore bridge, static, @XmlTransient methods plus methods declared in Throwable
        if (method.isBridge()
                || Modifier.isStatic(method.getModifiers())
                || method.isAnnotationPresent(XmlTransient.class)
                || method.getDeclaringClass().equals(Throwable.class)) {
            return false;
        }
        // Allow only public methods if PUBLIC_MEMBER access is requested
        if (accessType == XmlAccessType.PUBLIC_MEMBER && !Modifier.isPublic(method.getModifiers())) { 
            return false;
        }
        if (isGetter(method)) {
            // does nothing
        } else if (isSetter(method)) {
            if (!acceptSetters) {
                return false;
            }
        } else {
            // we accept only getters and setters
            return false;
        }
        // let JAXB annotations decide if NONE or FIELD access is requested
        if (accessType == XmlAccessType.NONE || accessType == XmlAccessType.FIELD) {
            return JAXBContextInitializer.checkJaxbAnnotation(method.getAnnotations());
        }
        // method accepted
        return true;
    }
    
    private static boolean isGetter(Method m) {
        Class<?> declaringClass = m.getDeclaringClass();
        if (m.getReturnType() != Void.class && m.getParameterTypes().length == 0) {
            final int index = getterIndex(m.getName());
            if (index != -1) {
                String setterName = "set" + m.getName().substring(index);
                Class<?> paramTypes = m.getReturnType();
                Method setter = getDeclaredMethod(declaringClass, setterName, paramTypes);
                if (setter != null && !setter.isAnnotationPresent(XmlTransient.class)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static boolean isSetter(Method m) {
        Class<?> declaringClass = m.getDeclaringClass();
        boolean isVoidReturnType = m.getReturnType() == Void.class || m.getReturnType() == Void.TYPE; 
        if (isVoidReturnType && m.getParameterTypes().length == 1 && m.getName().startsWith("set")) {
            String getterName = "get" + m.getName().substring(3);
            Class<?> setterParamType = m.getParameterTypes()[0];
            Method getter = getDeclaredMethod(declaringClass, getterName);
            if (getter != null && getter.getReturnType().equals(setterParamType)
                    && !getter.isAnnotationPresent(XmlTransient.class)) {
                return true;
            }
        }
        return false;
    }

    private static int getterIndex(String methodName) {
        if (methodName.startsWith("is")) {
            return 2;
        }
        if (methodName.startsWith("get")) {
            return 3;
        }
        return -1;
    }

    private static Method getDeclaredMethod(Class<?> cls, String methodName, Class<?>... paramTypes) {
        try {
            return cls.getDeclaredMethod(methodName, paramTypes);
        } catch (Exception e) {
            return null;
        }
    }

    static Class<?> getFieldType(Field f) {
        XmlJavaTypeAdapter adapter = getFieldXJTA(f);
        Class<?> adapterType = getTypeFromXmlAdapter(adapter);
        return adapterType != null ? adapterType : f.getType();
    }

    static Class<?> getMethodReturnType(Method m) {
        XmlJavaTypeAdapter adapter = getMethodXJTA(m);
        Class<?> adapterType = getTypeFromXmlAdapter(adapter);
        return adapterType != null ? adapterType : m.getReturnType(); 
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static Object getFieldValue(Field f, Object target) throws Exception {
        XmlJavaTypeAdapter adapterAnnotation = getFieldXJTA(f);
        XmlAdapter adapter = getXmlAdapter(adapterAnnotation);
        return adapter != null ? adapter.marshal(f.get(target)) : f.get(target); 
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static Object getMethodValue(Method m, Object target) throws Exception {
        XmlJavaTypeAdapter adapterAnnotation = getMethodXJTA(m);
        XmlAdapter adapter = getXmlAdapter(adapterAnnotation);
        return adapter != null ? adapter.marshal(m.invoke(target)) : m.invoke(target); 
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void setFieldValue(Field f, Object target, Object value) throws Exception {
        XmlJavaTypeAdapter xjta = getFieldXJTA(f);
        XmlAdapter adapter = getXmlAdapter(xjta);
        f.set(target, adapter != null ? adapter.unmarshal(value) : value);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    static void setMethodValue(Method getter, Method setter, Object target, Object value) throws Exception {
        XmlJavaTypeAdapter xjta = getMethodXJTA(getter);
        XmlAdapter adapter = getXmlAdapter(xjta);
        setter.invoke(target, adapter != null ? adapter.unmarshal(value) : value);
    }

    @SuppressWarnings("rawtypes") 
    static XmlAdapter getXmlAdapter(XmlJavaTypeAdapter adapterAnnotation)
        throws InstantiationException, IllegalAccessException {
        return adapterAnnotation != null ? adapterAnnotation.value().newInstance() : null;
    }

    static XmlJavaTypeAdapter getFieldXJTA(final Field f) {
        XmlJavaTypeAdapter adapter = f.getAnnotation(XmlJavaTypeAdapter.class);
        if (adapter == null) {
            adapter = f.getType().getAnnotation(XmlJavaTypeAdapter.class);
        }
        if (adapter == null) {
            XmlJavaTypeAdapters adapters = f.getDeclaringClass().getPackage().getAnnotation(XmlJavaTypeAdapters.class);
            if (adapters != null) {
                for (XmlJavaTypeAdapter candidate : adapters.value()) {
                    if (candidate != null && candidate.type().equals(f.getType())) {
                        adapter = candidate;
                        break;
                    }
                }
            }
        }
        return adapter;
    }

    static XmlJavaTypeAdapter getMethodXJTA(final Method m) {
        XmlJavaTypeAdapter adapter = m.getAnnotation(XmlJavaTypeAdapter.class);
        if (adapter == null) {
            adapter = m.getReturnType().getAnnotation(XmlJavaTypeAdapter.class);
        }
        if (adapter == null) {
            XmlJavaTypeAdapters adapters = m.getDeclaringClass().getPackage().getAnnotation(XmlJavaTypeAdapters.class);
            if (adapters != null) {
                for (XmlJavaTypeAdapter candidate : adapters.value()) {
                    if (candidate != null && candidate.type().equals(m.getGenericReturnType())) {
                        adapter = candidate;
                        break;
                    }
                }
            }
        }
        return adapter;
    }

    static Class<?> getTypeFromXmlAdapter(XmlJavaTypeAdapter xjta) {
        if (xjta != null) {
            Class<?> c2 = xjta.value();
            Type sp = c2.getGenericSuperclass();
            while (!XmlAdapter.class.equals(c2) && c2 != null) {
                sp = c2.getGenericSuperclass();
                c2 = c2.getSuperclass();
            }
            if (sp instanceof ParameterizedType) {
                return (Class<?>)((ParameterizedType)sp).getActualTypeArguments()[0];
            }
        }
        return null;
    }

}