/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.jcr.resource.internal.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.value.BooleanValue;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Maps;

/**
 * Testcase for {@link JcrPropertyMapCacheEntry}
 */
public class JcrPropertyMapCacheEntryTest {

    private final ValueFactory vf = ValueFactoryImpl.getInstance();
    private final Session session = mock(Session.class);
    private final Node node = mock(Node.class);
    
    @Before
    public void before() throws Exception {
        when(session.getValueFactory()).thenReturn(vf);
        when(node.getSession()).thenReturn(session);
    }

    @Test
    public void testByteArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Byte[0], node);
        new JcrPropertyMapCacheEntry(new byte[0], node);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testShortArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Short[0], node);
        new JcrPropertyMapCacheEntry(new short[0], node);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testIntArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Integer[0], node);
        new JcrPropertyMapCacheEntry(new int[0], node);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testLongArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Long[0], node);
        new JcrPropertyMapCacheEntry(new long[0], node);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testFloatArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Float[0], node);
        new JcrPropertyMapCacheEntry(new float[0], node);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testDoubleArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Double[0], node);
        new JcrPropertyMapCacheEntry(new double[0], node);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testBooleanArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Boolean[0], node);
        new JcrPropertyMapCacheEntry(new boolean[0], node);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testCharArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Character[0], node);
        new JcrPropertyMapCacheEntry(new char[0], node);
        verifyNoMoreInteractions(node);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCannotStore() throws Exception {
        Object value = new TestClass();
        new JcrPropertyMapCacheEntry(value, node);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCannotStoreArray() throws Exception {
        Object value = new TestClass();
        new JcrPropertyMapCacheEntry(new Object[] {value}, node);
    }
    
    @Test
    public void testGetPropertyValueOrNull() throws Exception {
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(true, node);
        assertEquals(Boolean.TRUE, entry.getPropertyValueOrNull());
    }
    
    @Test
    public void testGetPropertyValueOrNullWithRepositoryException() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.BINARY);
        when(prop.getValue()).thenThrow(new RepositoryException());
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        assertNull(entry.getPropertyValueOrNull());
    }
    
    @Test
    public void testInputStreamToString() throws Exception {
        InputStream in = new ByteArrayInputStream("test".getBytes());
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);
        
        String result = entry.convertToType(String.class, node, null);
        assertEquals("test", result);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testInputStreamToLong() throws Exception {
        InputStream in = new ByteArrayInputStream("10".getBytes());
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);

        Long result = entry.convertToType(Long.class, node, null);
        assertNull(result);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testInputStreamToIntegerArray() throws Exception {
        InputStream in = new ByteArrayInputStream("10".getBytes());
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);

        Integer[] result = entry.convertToType(Integer[].class, node, null);
        assertNotNull(result);
        assertEquals(0, result.length);
        verifyNoMoreInteractions(node);
    }
    
    @Test
    public void testBinaryPropertyToInteger() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.BINARY);
        when(prop.getStream()).thenReturn(new ByteArrayInputStream("10".getBytes()));
        when(prop.getValue()).thenReturn(vf.createValue(new ByteArrayInputStream("10".getBytes())));
        when(prop.getLength()).thenReturn(2L);
        
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        Integer result = entry.convertToType(Integer.class, node, null);
        assertEquals(Integer.valueOf(2), result);
        
        verify(prop, times(2)).isMultiple();
        verify(prop).getValue();
        verify(prop).getType();
        verify(prop).getLength();
        verifyNoMoreInteractions(prop);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testBinaryPropertyToDoubleArray() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.BINARY);
        when(prop.getStream()).thenReturn(new ByteArrayInputStream("10.7".getBytes()));
        when(prop.getValue()).thenReturn(vf.createValue(new ByteArrayInputStream("10.7".getBytes())));
        when(prop.getLength()).thenReturn(4L);
        when(prop.getLengths()).thenThrow(new ValueFormatException("single-valued"));

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        Double[] result = entry.convertToType(Double[].class, node, null);
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(Double.valueOf(4.0), result[0]);
        
        verify(prop, times(2)).isMultiple();
        verify(prop).getValue();
        verify(prop).getType();
        verify(prop).getLength();
        verifyNoMoreInteractions(prop);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testMvBinaryPropertyToFloatArray() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.BINARY);
        when(prop.isMultiple()).thenReturn(true);
        when(prop.getValue()).thenThrow(new ValueFormatException("multi-valued"));
        Value[] vs = new Value[] {vf.createValue("10.7", PropertyType.BINARY), vf.createValue("10.7", PropertyType.BINARY)};
        when(prop.getValues()).thenReturn(vs);
        when(prop.getLength()).thenThrow(new ValueFormatException("multi-valued"));
        when(prop.getLengths()).thenReturn(new long[] {4L, 4L});

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        Float[] result = entry.convertToType(Float[].class, node, null);
        assertNotNull(result);
        assertEquals(2, result.length);
        assertEquals(Float.valueOf(4.0f), result[0]);

        verify(prop, times(2)).isMultiple();
        verify(prop).getValues();
        verify(prop).getType();
        verify(prop, times(2)).getLengths();
        verifyNoMoreInteractions(prop);
        verifyNoMoreInteractions(node);
    }
    
    @Test
    public void testInputStreamToObjectInputStream() throws Exception {
        InputStream in = new ByteArrayInputStream("value".getBytes());

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);
        ObjectInputStream result = entry.convertToType(ObjectInputStream.class, node, null);
        assertNull(result); // TODO: is this the expected result?
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testInputStreamToSerializableSameType() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(Maps.newHashMap());
        }
        
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(new ByteArrayInputStream(out.toByteArray()), node);
        // same type
        Map<?,?> result = entry.convertToType(HashMap.class, node, null);
        assertNotNull(result);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testInputStreamToSerializable() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(new LinkedHashMap<>());
        }

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(new ByteArrayInputStream(out.toByteArray()), node);
        // different type that cannot be converted
        Calendar result = entry.convertToType(Calendar.class, node, LinkedHashMap.class.getClassLoader());
        assertNull(result);

        verifyNoMoreInteractions(node);
    }
    
    @Test
    public void testBinaryPropertyToObjectInputStream() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.BINARY);
        when(prop.getStream()).thenReturn(new ByteArrayInputStream("value".getBytes()));
        when(prop.getValue()).thenReturn(vf.createValue(new ByteArrayInputStream("value".getBytes())));

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        ObjectInputStream result = entry.convertToType(ObjectInputStream.class, node, null);
        assertNull(result); // TODO: is this the expected result?

        verify(prop, times(2)).isMultiple();
        verify(prop).getValue();
        verify(prop).getType();
        verifyNoMoreInteractions(prop);
        verifyNoMoreInteractions(node);
    }
    
    @Test
    public void testMvPropertyToBoolean() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.STRING);
        when(prop.isMultiple()).thenReturn(true);
        when(prop.getValue()).thenThrow(new ValueFormatException("multi-valued"));
        Value[] vs = new Value[] {vf.createValue("true"), vf.createValue("false")};
        when(prop.getValues()).thenReturn(vs);

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        assertTrue(entry.isArray());
        
        Boolean result = entry.convertToType(Boolean.class, node, null);
        assertNotNull(result);
        assertTrue(result);

        verify(prop, times(2)).isMultiple();
        verify(prop).getValues();
        verify(prop).getType();
        verifyNoMoreInteractions(prop);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testEmptyMvPropertyToString() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.STRING);
        when(prop.isMultiple()).thenReturn(true);
        when(prop.getValue()).thenThrow(new ValueFormatException("multi-valued"));
        Value[] vs = new Value[0];
        when(prop.getValues()).thenReturn(vs);

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        assertTrue(entry.isArray());
        
        String result = entry.convertToType(String.class, node, null);
        assertNull(result);

        verify(prop, times(2)).isMultiple();
        verify(prop).getValues();
        verify(prop).getType();
        verifyNoMoreInteractions(prop);
        verifyNoMoreInteractions(node);
    }
    
    @Test
    public void testConversionFails() throws RepositoryException {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.STRING);
        when(prop.isMultiple()).thenReturn(false);
        when(prop.getValue()).thenReturn(vf.createValue("string"));

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        assertFalse(entry.isArray());

        Short result = entry.convertToType(Short.class, node, null);
        assertNull(result);

        verify(prop, times(2)).isMultiple();
        verify(prop).getValue();
        verify(prop).getType();
        verifyNoMoreInteractions(prop);
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testStringToValue() throws RepositoryException {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.STRING);
        when(prop.isMultiple()).thenReturn(false);
        when(prop.getValue()).thenReturn(vf.createValue("string"));

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        assertFalse(entry.isArray());

        Value result = entry.convertToType(Value.class, node, null);
        assertNotNull(result);
        assertEquals(PropertyType.STRING, result.getType());
        assertEquals("string", result.getString());

        verify(prop, times(2)).isMultiple();
        verify(prop).getValue();
        verify(prop).getType();
        verify(node).getSession();
        verifyNoMoreInteractions(prop, node);
    }
    
    @Test
    public void testConvertToSameType() throws Exception {
        Calendar cal = Calendar.getInstance();
        
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(cal, node);
        Calendar result = entry.convertToType(Calendar.class, node, null);
        
        assertSame(cal, result);
        verify(node).getSession();
        verifyNoMoreInteractions(node);
    }

    @Test
    public void testStringToProperty() throws Exception {
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry("value", node);
        Property result = entry.convertToType(Property.class, node, null);
        assertNull(result); // TODO is this expected?
        
        verify(node).getSession();
        verify(session).getValueFactory();
        verifyNoMoreInteractions(node, session);
    }

    @Test
    public void testPropertyToProperty() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.BOOLEAN);
        when(prop.getValue()).thenReturn(BooleanValue.valueOf("true"));
        
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        Property result = entry.convertToType(Property.class, node, null);
        assertSame(prop, result);
        
        verifyNoMoreInteractions(node);
        verify(prop).getType();
        verify(prop).getValue();
        verify(prop, times(2)).isMultiple();
        verifyNoMoreInteractions(prop);
    }

    @Test
    public void testCreateFromSerializable() throws Exception {
        Object value = new HashMap<>();
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(value, node);
        Object propValue = entry.getPropertyValue();
        assertTrue(propValue instanceof HashMap);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testCreateFromNonSerializableComplexValue() throws Exception {
        Object value = new TestClass();
        new JcrPropertyMapCacheEntry(value, node);
    }
    
    private static final class TestClass {}
    
    @Test(expected = RepositoryException.class)
    public void testCreateFromSerializeComplexValueWithUnserializableField() throws Exception {
        // this class cannot be serialized and throws an exception at runtime (as Optional is not serializable)
        Object value = new TestClass2();
        new JcrPropertyMapCacheEntry(value, node);
    }
    
    private static final class TestClass2 implements Serializable {
        Optional<String> value;
        
        public TestClass2() {
            this.value = Optional.empty();
        }
    }
}