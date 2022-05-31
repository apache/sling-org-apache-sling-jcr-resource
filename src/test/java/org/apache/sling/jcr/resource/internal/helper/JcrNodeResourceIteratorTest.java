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

import java.util.Arrays;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.iterator.NodeIteratorAdapter;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.jcr.resource.internal.HelperData;
import org.apache.sling.jcr.resource.internal.helper.jcr.JcrNodeResourceIterator;
import org.apache.sling.testing.mock.jcr.MockJcr;

import junit.framework.TestCase;

public class JcrNodeResourceIteratorTest extends TestCase {

    private HelperData getHelperData() {
        return new HelperData(new AtomicReference<DynamicClassLoaderManager>(), new AtomicReference<URIProvider[]>());
    }

    public void testEmpty() {
        NodeIterator ni = new NodeIteratorAdapter(Collections.emptyIterator());

        JcrNodeResourceIterator ri = new JcrNodeResourceIterator(null, null, null, ni, getHelperData(), null);

        assertFalse(ri.hasNext());

        try {
            ri.next();
            fail("Expected no element in the iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testSingle() throws RepositoryException {
        String path = "/parent/path/node";
        Session session = MockJcr.newSession();
        Node node = JcrUtils.getOrCreateByPath(path, "nt:folder", session);
        NodeIterator ni = new NodeIteratorAdapter(Collections.singleton(node));
        JcrNodeResourceIterator ri = new JcrNodeResourceIterator(null, null, null, ni, getHelperData(), null);

        assertTrue(ri.hasNext());
        Resource res = ri.next();
        assertEquals(path, res.getPath());
        assertEquals(node.getPrimaryNodeType().getName(), res.getResourceType());

        assertFalse(ri.hasNext());

        try {
            ri.next();
            fail("Expected no element in the iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testMulti() throws RepositoryException {
        int numNodes = 10;
        String pathBase = "/parent/path/node/";
        Session session = MockJcr.newSession();
        Node[] nodes = new Node[numNodes];
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = JcrUtils.getOrCreateByPath(pathBase + i, "nt:folder", session);
        }
        NodeIterator ni = new NodeIteratorAdapter(Arrays.asList(nodes));
        JcrNodeResourceIterator ri = new JcrNodeResourceIterator(null, null, null, ni, getHelperData(), null);

        for (int i = 0; i < nodes.length; i++) {
            assertTrue(ri.hasNext());
            Resource res = ri.next();
            assertEquals(pathBase + i, res.getPath());
            assertEquals(nodes[i].getPrimaryNodeType().getName(), res.getResourceType());
        }

        assertFalse(ri.hasNext());

        try {
            ri.next();
            fail("Expected no element in the iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }

    public void testRoot() throws RepositoryException {
        String path = "/child";
        Session session = MockJcr.newSession();
        Node node = session.getRootNode().addNode("child");
        NodeIterator ni = new NodeIteratorAdapter(Collections.singleton(node));
        JcrNodeResourceIterator ri = new JcrNodeResourceIterator(null, "/", null, ni, getHelperData(), null);

        assertTrue(ri.hasNext());
        Resource res = ri.next();
        assertEquals(path, res.getPath());
        assertEquals(node.getPrimaryNodeType().getName(), res.getResourceType());

        assertFalse(ri.hasNext());

        try {
            ri.next();
            fail("Expected no element in the iterator");
        } catch (NoSuchElementException nsee) {
            // expected
        }
    }
}
