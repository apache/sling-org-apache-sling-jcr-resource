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

import java.util.NoSuchElementException;

import junit.framework.TestCase;

public class ResourcePathIteratorTest extends TestCase {

    public void testNull() {
        ResourcePathIterator rpi = new ResourcePathIterator(null);
        assertFalse(rpi.hasNext());

        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testEmpty() {
        ResourcePathIterator rpi = new ResourcePathIterator("");
        assertFalse(rpi.hasNext());

        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testRoot() {
        ResourcePathIterator rpi = new ResourcePathIterator("/");
        assertFalse(rpi.hasNext());

        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testSlashed() {
        ResourcePathIterator rpi = new ResourcePathIterator("/root/child");
        assertEquals("/root/child", rpi.next());
        assertEquals("/root", rpi.next());
        assertFalse(rpi.hasNext());
        
        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }
    
    public void testSlashedTrailingSlash1() {
        ResourcePathIterator rpi = new ResourcePathIterator("/root/child/");
        assertEquals("/root/child", rpi.next());
        assertEquals("/root", rpi.next());
        assertFalse(rpi.hasNext());
        
        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }
    
    public void testSlashedTrailingSlash2() {
        ResourcePathIterator rpi = new ResourcePathIterator("/root/child//");
        assertEquals("/root/child", rpi.next());
        assertEquals("/root", rpi.next());
        assertFalse(rpi.hasNext());

        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testDotted() {
        ResourcePathIterator rpi = new ResourcePathIterator("/root.child");
        assertEquals("/root.child", rpi.next());
        assertEquals("/root", rpi.next());
        assertFalse(rpi.hasNext());

        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testMixed() {
        ResourcePathIterator rpi = new ResourcePathIterator("/root/child.print.a4.html/with/suffix");
        assertEquals("/root/child.print.a4.html/with/suffix", rpi.next());
        assertEquals("/root/child.print.a4.html/with", rpi.next());
        assertEquals("/root/child.print.a4.html", rpi.next());
        assertEquals("/root/child.print.a4", rpi.next());
        assertEquals("/root/child.print", rpi.next());
        assertEquals("/root/child", rpi.next());
        assertEquals("/root", rpi.next());
        assertFalse(rpi.hasNext());

        try {
            rpi.next();
            fail("Expected NoSuchElementException after end of iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

}