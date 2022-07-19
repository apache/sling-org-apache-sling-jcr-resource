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

import org.apache.jackrabbit.value.BooleanValue;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.Session;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Calendar;
import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

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
        verifyZeroInteractions(node);
    }

    @Test
    public void testShortArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Short[0], node);
        new JcrPropertyMapCacheEntry(new short[0], node);
        verifyZeroInteractions(node);
    }

    @Test
    public void testIntArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Integer[0], node);
        new JcrPropertyMapCacheEntry(new int[0], node);
        verifyZeroInteractions(node);
    }

    @Test
    public void testLongArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Long[0], node);
        new JcrPropertyMapCacheEntry(new long[0], node);
        verifyZeroInteractions(node);
    }

    @Test
    public void testFloatArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Float[0], node);
        new JcrPropertyMapCacheEntry(new float[0], node);
        verifyZeroInteractions(node);
    }

    @Test
    public void testDoubleArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Double[0], node);
        new JcrPropertyMapCacheEntry(new double[0], node);
        verifyZeroInteractions(node);
    }

    @Test
    public void testBooleanArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Boolean[0], node);
        new JcrPropertyMapCacheEntry(new boolean[0], node);
        verifyZeroInteractions(node);
    }

    @Test
    public void testCharArray() throws Exception {
        new JcrPropertyMapCacheEntry(new Character[0], node);
        new JcrPropertyMapCacheEntry(new char[0], node);
        verifyZeroInteractions(node);
    }
    
    @Test
    public void testInputStreamToString() throws Exception {
        InputStream in = new ByteArrayInputStream("test".getBytes());
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);
        
        String result = entry.convertToType(String.class, node, null);
        assertEquals("test", result);
        verifyZeroInteractions(node);
    }

    @Test
    public void testInputStreamToLong() throws Exception {
        InputStream in = new ByteArrayInputStream("10".getBytes());
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);

        Long result = entry.convertToType(Long.class, node, null);
        assertNull(result);
        verifyZeroInteractions(node);
    }

    @Test
    public void testInputStreamToIntegerArray() throws Exception {
        InputStream in = new ByteArrayInputStream("10".getBytes());
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);

        Integer[] result = entry.convertToType(Integer[].class, node, null);
        assertNotNull(result);
        assertEquals(0, result.length);
        verifyZeroInteractions(node);
    }
    
    @Test
    public void testBinaryPropertyToLong() throws Exception {
        Property prop = mock(Property.class);
        when(prop.getType()).thenReturn(PropertyType.BINARY);
        when(prop.getStream()).thenReturn(new ByteArrayInputStream("10".getBytes()));
        when(prop.getValue()).thenReturn(vf.createValue(new ByteArrayInputStream("10".getBytes())));
        when(prop.getLength()).thenReturn(2L);
        
        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(prop);
        Long result = entry.convertToType(Long.class, node, null);
        assertEquals(Long.valueOf(2), result);
        
        verify(prop, times(2)).isMultiple();
        verify(prop).getValue();
        verify(prop).getType();
        verify(prop).getLength();
        verifyNoMoreInteractions(prop);
        
        verifyZeroInteractions(node);
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
        verifyZeroInteractions(node);
    }
    
    @Test
    public void testInputStreamToObjectInputStream() throws Exception {
        InputStream in = new ByteArrayInputStream("value".getBytes());

        JcrPropertyMapCacheEntry entry = new JcrPropertyMapCacheEntry(in, node);
        ObjectInputStream result = entry.convertToType(ObjectInputStream.class, node, null);
        assertNull(result); // TODO: is this the expected result?
        verifyZeroInteractions(node);
    }
    
    @Test
    public void testBinaryPropertyToObjectInputStream() throws Exception {
        InputStream in = new ByteArrayInputStream("value".getBytes());

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
        verifyZeroInteractions(node);
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
        
        verifyZeroInteractions(node);
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
    
    @Test
    public void testCreateFromUnstorableValue() throws Exception {
        try {
            Object value = new TestClass();
            new JcrPropertyMapCacheEntry(value, node);
            fail("IllegalArgumentException expected");
        } catch (IllegalArgumentException e) {
            // success
        }
    }
    
    private static final class TestClass {};
}