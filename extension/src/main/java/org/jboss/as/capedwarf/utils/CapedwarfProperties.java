/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.capedwarf.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * GAE uses 1 app per env, we run multi app support,
 * hence system properties need to be per app.
 *
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 */
@SuppressWarnings("NullableProblems")
public class CapedwarfProperties extends Properties {
    private Map<ClassLoader, Properties> map = new HashMap<ClassLoader, Properties>();

    public CapedwarfProperties(Properties original) {
        super(original);
    }

    public void init(ClassLoader classLoader) {
        Properties properties = new Properties();
        properties.putAll(defaults);
        map.put(classLoader, properties);
    }

    public void clean(ClassLoader classLoader) {
        map.remove(classLoader);
    }

    protected Properties get() {
        Properties properties = map.get(SecurityActions.setTCCL(null));
        return (properties != null) ? properties : defaults;
    }

    @Override
    public Object setProperty(String key, String value) {
        return get().setProperty(key, value);
    }

    @Override
    public void load(Reader reader) throws IOException {
        get().load(reader);
    }

    @Override
    public void load(InputStream inStream) throws IOException {
        get().load(inStream);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void save(OutputStream out, String comments) {
        get().save(out, comments);
    }

    @Override
    public void store(Writer writer, String comments) throws IOException {
        get().store(writer, comments);
    }

    @Override
    public void store(OutputStream out, String comments) throws IOException {
        get().store(out, comments);
    }

    @Override
    public void loadFromXML(InputStream in) throws IOException {
        get().loadFromXML(in);
    }

    @Override
    public void storeToXML(OutputStream os, String comment) throws IOException {
        get().storeToXML(os, comment);
    }

    @Override
    public void storeToXML(OutputStream os, String comment, String encoding) throws IOException {
        get().storeToXML(os, comment, encoding);
    }

    @Override
    public String getProperty(String key) {
        return get().getProperty(key);
    }

    @Override
    public String getProperty(String key, String defaultValue) {
        return get().getProperty(key, defaultValue);
    }

    @Override
    public Enumeration<?> propertyNames() {
        return get().propertyNames();
    }

    @Override
    public Set<String> stringPropertyNames() {
        return get().stringPropertyNames();
    }

    @Override
    public void list(PrintStream out) {
        get().list(out);
    }

    @Override
    public void list(PrintWriter out) {
        get().list(out);
    }

    @Override
    public int size() {
        return get().size();
    }

    @Override
    public boolean isEmpty() {
        return get().isEmpty();
    }

    @Override
    public Enumeration<Object> keys() {
        return get().keys();
    }

    @Override
    public Enumeration<Object> elements() {
        return get().elements();
    }

    @Override
    public boolean contains(Object value) {
        return get().contains(value);
    }

    @Override
    public boolean containsValue(Object value) {
        return get().containsValue(value);
    }

    @Override
    public boolean containsKey(Object key) {
        return get().containsKey(key);
    }

    @Override
    public Object get(Object key) {
        return get().get(key);
    }

    @Override
    public Object put(Object key, Object value) {
        return get().put(key, value);
    }

    @Override
    public Object remove(Object key) {
        return get().remove(key);
    }

    @Override
    public void putAll(Map<?, ?> t) {
        get().putAll(t);
    }

    @Override
    public void clear() {
        get().clear();
    }

    @SuppressWarnings("CloneDoesntCallSuperClone")
    @Override
    public Object clone() {
        return get().clone();
    }

    @Override
    public String toString() {
        return get().toString();
    }

    @Override
    public Set<Object> keySet() {
        return get().keySet();
    }

    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return get().entrySet();
    }

    @Override
    public Collection<Object> values() {
        return get().values();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return get().equals(o);
    }

    @Override
    public int hashCode() {
        return get().hashCode();
    }
}
