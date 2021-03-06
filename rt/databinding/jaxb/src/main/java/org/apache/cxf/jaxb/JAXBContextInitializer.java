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

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Set;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.cxf.service.ServiceModelVisitor;
import org.apache.cxf.service.model.MessagePartInfo;
import org.apache.cxf.service.model.ServiceInfo;

/**
 * Walks the service model and sets up the classes for the context.
 */
class JAXBContextInitializer extends ServiceModelVisitor {

    private Set<Class<?>> classes;

    public JAXBContextInitializer(ServiceInfo serviceInfo, Set<Class<?>> classes) {
        super(serviceInfo);
        this.classes = classes;
    }

    @Override
    public void begin(MessagePartInfo part) {
        Class<?> clazz = part.getTypeClass();
        if (clazz == null) {
            return;
        }

        if (Exception.class.isAssignableFrom(clazz)) {
            //exceptions are handled special, make sure we mark it
            part.setProperty(JAXBDataBinding.class.getName() + ".CUSTOM_EXCEPTION",
                             Boolean.TRUE);
        }
        boolean isFromWrapper = part.getMessageInfo().getOperation().isUnwrapped();
        if (isFromWrapper
            && clazz.isArray()
            && !Byte.TYPE.equals(clazz.getComponentType())) {
            clazz = clazz.getComponentType();
        }

        Type genericType = (Type) part.getProperty("generic.type");
        if (genericType != null) {
            boolean isList = Collection.class.isAssignableFrom(clazz);
            if (isFromWrapper) {
                if (genericType instanceof Class
                    && ((Class)genericType).isArray()) {
                    Class cl2 = (Class)genericType;
                    if (cl2.isArray()
                        && !Byte.TYPE.equals(cl2.getComponentType())) {
                        genericType = cl2.getComponentType();
                    }
                    addType(genericType);
                } else if (!isList) {
                    addType(genericType);
                }
            } else {
                addType(genericType);
            }

            if (isList
                && genericType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericType;
                if (pt.getActualTypeArguments().length > 0
                    && pt.getActualTypeArguments()[0] instanceof Class) {

                    Class<? extends Object> arrayCls =
                        Array.newInstance((Class) pt.getActualTypeArguments()[0], 0).getClass();
                    clazz = arrayCls;
                    part.setTypeClass(clazz);
                    if (isFromWrapper) {
                        addType(clazz.getComponentType());
                    }
                } else if (pt.getActualTypeArguments().length > 0
                    && pt.getActualTypeArguments()[0] instanceof GenericArrayType) {
                    GenericArrayType gat = (GenericArrayType)pt.getActualTypeArguments()[0];
                    gat.getGenericComponentType();
                    Class<? extends Object> arrayCls =
                        Array.newInstance((Class) gat.getGenericComponentType(), 0).getClass();
                    clazz = Array.newInstance(arrayCls, 0).getClass();
                    part.setTypeClass(clazz);
                    if (isFromWrapper) {
                        addType(clazz.getComponentType());
                    }
                }
            }
            if (isFromWrapper && isList) {
                clazz = null;
            }
        }
        if (clazz != null) {
            addClass(clazz);
        }
    }

    private void addType(Type cls) {
        if (cls instanceof Class) {
            addClass((Class)cls);
        } else if (cls instanceof ParameterizedType) {
            for (Type t2 : ((ParameterizedType)cls).getActualTypeArguments()) {
                addType(t2);
            }
        } else if (cls instanceof GenericArrayType) {
            GenericArrayType gt = (GenericArrayType)cls;
            Class ct = (Class) gt.getGenericComponentType();
            ct = Array.newInstance(ct, 0).getClass();

            addClass(ct);
        }
    }


    private  void addClass(Class<?> cls) {
        if (cls.isArray() && cls.getComponentType().isPrimitive()) {
            return;
        }
        if (Throwable.class.isAssignableFrom(cls)) {
            if (!Throwable.class.equals(cls)
                && !Exception.class.equals(cls)) {
                walkReferences(cls);
            }
            addClass(String.class);
        } else {
            cls = JAXBUtils.getValidClass(cls);
            if (null != cls) {
                if (classes.contains(cls)) {
                    return;
                }
                if (cls.getSuperclass() != null) {
                    //JAXB should do this, but it doesn't always.
                    //in particular, older versions of jaxb don't
                    addClass(cls.getSuperclass());
                }

                if (cls.isEnum()) {
                    // The object factory stuff doesn't work for enums
                    classes.add(cls);
                }
                classes.add(cls);
                walkReferences(cls);
            }
        }
    }

    private void walkReferences(Class<?> cls) {
        if (cls == null) {
            return;
        }
        if (cls.getName().startsWith("java.")
            || cls.getName().startsWith("javax.")) {
            return;
        }
        //walk the public fields/methods to try and find all the classes. JAXB will only load the
        //EXACT classes in the fields/methods if they are in a different package. Thus,
        //subclasses won't be found and the xsi:type stuff won't work at all.
        //We'll grab the public field/method types and then add the ObjectFactory stuff
        //as well as look for jaxb.index files in those packages.

        XmlAccessorType accessorType = cls.getAnnotation(XmlAccessorType.class);
        if (accessorType == null && cls.getPackage() != null) {
            accessorType = cls.getPackage().getAnnotation(XmlAccessorType.class);
        }
        XmlAccessType accessType = accessorType != null ? accessorType.value() : XmlAccessType.PUBLIC_MEMBER;

        if (accessType != XmlAccessType.PROPERTY) {   // only look for fields if we are instructed to
            //fields are accessible even if not public, must look at the declared fields
            //then walk to parents declared fields, etc...
            Field fields[] = cls.getDeclaredFields(); 
            for (Field f : fields) {
                if (isFieldAccepted(f, accessType)) {
                    addType(f.getGenericType());
                }
            }
            walkReferences(cls.getSuperclass());
        }

        if (accessType != XmlAccessType.FIELD) {   // only look for methods if we are instructed to
            Method methods[] = cls.getDeclaredMethods(); 
            for (Method m : methods) {
                if (isMethodAccepted(m, accessType)) {
                    addType(m.getGenericReturnType());
                    for (Type t : m.getGenericParameterTypes()) {
                        addType(t);
                    }
                }
            }
        }
    }

    /**
     * Checks if the field is accepted as a JAXB property.
     */
    static boolean isFieldAccepted(Field field, XmlAccessType accessType) {
        // We only accept non static fields which are not marked @XmlTransient
        if (Modifier.isStatic(field.getModifiers()) || field.isAnnotationPresent(XmlTransient.class)) {
            return false;
        }
        if (accessType == XmlAccessType.PUBLIC_MEMBER 
            && !Modifier.isPublic(field.getModifiers())) {
            return false;
        }
        if (field.getAnnotation(XmlJavaTypeAdapter.class) != null) {
            return false;
        }
        if (accessType == XmlAccessType.NONE
            || accessType == XmlAccessType.PROPERTY) {
            return checkJaxbAnnotation(field.getAnnotations());
        } else {
            return true;
        }
    }

    /**
     * Checks if the method is accepted as a JAXB property getter.
     */
    static boolean isMethodAccepted(Method method, XmlAccessType accessType) {
        // We only accept non static property getters which are not marked @XmlTransient
        if (Modifier.isStatic(method.getModifiers()) 
            || method.isAnnotationPresent(XmlTransient.class)
            || !Modifier.isPublic(method.getModifiers())) {
            return false;
        }

        // must not have parameters and return type must not be void
        if (method.getReturnType() == Void.class 
            || method.getParameterTypes().length != 0
            || method.getDeclaringClass().equals(Throwable.class)) {
            return false;
        }

        boolean isPropGetter = method.getName().startsWith("get") || method.getName().startsWith("is");

        if (!isPropGetter 
            || method.getAnnotation(XmlJavaTypeAdapter.class) != null) {
            return false;
        }
        int beginIndex = 3;
        if (method.getName().startsWith("is")) {
            beginIndex = 2;
        }
        try {
            method.getDeclaringClass().getMethod("set" + method.getName().substring(beginIndex),
                                                 new Class[] {method.getReturnType()});
        } catch (Exception e) {
            //getter, but no setter
            return false;
        }

        if (accessType == XmlAccessType.NONE
            || accessType == XmlAccessType.FIELD) {
            return checkJaxbAnnotation(method.getAnnotations());
        } else {
            return true;
        }
    }

    /**
     * Checks if there are JAXB annotations among the annotations of the class member.
     * @param annotations the array of annotations from the class member
     * @return true if JAXB annotations are present, false otherwise
     */
    private static boolean checkJaxbAnnotation(Annotation[] annotations) {
        // must check if there are any jaxb annotations
        Package jaxbAnnotationsPackage = XmlElement.class.getPackage();
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getPackage() == jaxbAnnotationsPackage) {
                return true;
            }
        }
        return false;
    }
}