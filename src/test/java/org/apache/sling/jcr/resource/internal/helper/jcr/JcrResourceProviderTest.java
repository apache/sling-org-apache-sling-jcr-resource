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
package org.apache.sling.jcr.resource.internal.helper.jcr;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.jcr.resource.internal.HelperData;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

import static javax.jcr.nodetype.NodeType.NT_UNSTRUCTURED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JcrResourceProviderTest extends SlingRepositoryTestBase {

    JcrResourceProvider jcrResourceProvider;
    Session session;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // create the session
        session = getSession();
        Repository repo = getRepository();
        ComponentContext ctx = mock(ComponentContext.class);
        when(ctx.locateService(Mockito.anyString(), Mockito.any(ServiceReference.class))).thenReturn(repo);
        jcrResourceProvider = new JcrResourceProvider();
        jcrResourceProvider.activate(ctx);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        try {
            if (session.nodeExists("/parent")) {
                session.removeItem("/parent");
                session.save();
            }
        } finally {
            jcrResourceProvider.deactivate();
            super.tearDown();
        }
    }

    private @NotNull JcrProviderState createProviderState() {
        return new JcrProviderState(session, new HelperData(new AtomicReference<>(), new AtomicReference<>()), false);
    }

    private @NotNull ResolveContext mockResolveContext() {
        ResolveContext ctx = mock(ResolveContext.class);
        when(ctx.getProviderState()).thenReturn(createProviderState());
        return ctx;
    }

    @Test
    public void testAdaptTo_Principal() {
        ResolveContext ctx = mockResolveContext();
        Assert.assertNotNull(jcrResourceProvider.adaptTo(ctx, Principal.class));
    }

    @Test
    public void testOrderBefore() throws RepositoryException, PersistenceException {
        Node parentNode = session.getRootNode().addNode("parent", NT_UNSTRUCTURED);
        parentNode.addNode("child1", NT_UNSTRUCTURED);
        parentNode.addNode("child2", NT_UNSTRUCTURED);
        parentNode.addNode("child3", NT_UNSTRUCTURED);
        session.save();

        ResolveContext ctx = mockResolveContext();
        Resource parent = jcrResourceProvider.getResource(ctx, "/parent", ResourceContext.EMPTY_CONTEXT, null);
        Assert.assertNotNull(parent);
        // order with invalid names
        try {
            jcrResourceProvider.orderBefore(ctx, parent, "child3", "child4");
        } catch (PersistenceException e) {
            // expected
        }
        // order successfully
        Assert.assertTrue(jcrResourceProvider.orderBefore(ctx, parent, "child2", "child1"));

        // order already established
        Assert.assertFalse(jcrResourceProvider.orderBefore(ctx, parent, "child2", "child1"));

        // order child2 at end
        Assert.assertTrue(jcrResourceProvider.orderBefore(ctx, parent, "child2", null));

        // order child2 at end again
        Assert.assertFalse(jcrResourceProvider.orderBefore(ctx, parent, "child2", null));

        // make sure nothing is persisted until save
        jcrResourceProvider.revert(ctx);

        Assert.assertTrue(jcrResourceProvider.orderBefore(ctx, parent, "child2", null));
    }

    @Test
    public void testGetParent() throws Exception {
        Node parentNode = session.getRootNode().addNode("parent", NT_UNSTRUCTURED);
        Node child = parentNode.addNode("child", NT_UNSTRUCTURED);
        Node grandchild = child.addNode("grandchild", NT_UNSTRUCTURED);
        session.save();

        ResolveContext ctx = mockResolveContext();
        Resource rootResource = jcrResourceProvider.getResource(ctx, PathUtils.ROOT_PATH, ResourceContext.EMPTY_CONTEXT, null);
        Resource parentResource = jcrResourceProvider.getResource(ctx, parentNode.getPath(), ResourceContext.EMPTY_CONTEXT, rootResource);
        Resource childResource = jcrResourceProvider.getResource(ctx, child.getPath(), ResourceContext.EMPTY_CONTEXT, parentResource);
        Resource grandChildResource = jcrResourceProvider.getResource(ctx, grandchild.getPath(), ResourceContext.EMPTY_CONTEXT, childResource);
        assertResources(rootResource, parentResource, childResource, grandChildResource);

        assertParent(jcrResourceProvider.getParent(ctx, grandChildResource), child.getPath());
        assertParent(jcrResourceProvider.getParent(ctx, childResource), parentNode.getPath());
        assertParent(jcrResourceProvider.getParent(ctx, parentResource), PathUtils.ROOT_PATH);
        assertNull(jcrResourceProvider.getParent(ctx, rootResource));
    }

    @Test
    public void testGetParentDifferentResource() {
        Resource r = mock(Resource.class);
        when(r.getPath()).thenReturn("/test/path");
        Resource parent = jcrResourceProvider.getParent(mockResolveContext(), r);
        assertFalse(parent instanceof JcrNodeResource);
    }
    
    @Test
    public void testGetNodeType() throws RepositoryException {
        
        Map<String,Object> properties = new HashMap<>();
        ResolveContext<JcrProviderState> context = mock(ResolveContext.class);
        // wire a mock jcrProviderState into the existing session (backed by a real repo)
        JcrProviderState jcrProviderState = mock(JcrProviderState.class);
        when(context.getProviderState()).thenReturn(jcrProviderState);
        when(jcrProviderState.getSession()).thenReturn(session);

        assertEquals(null, JcrResourceProvider.getNodeType(null, null));
        properties.put("sling:alias","alias");
        assertEquals(null, JcrResourceProvider.getNodeType(properties, context));
        properties.put(JcrConstants.JCR_PRIMARYTYPE, "nt:unstructured");
        assertEquals("nt:unstructured", JcrResourceProvider.getNodeType(properties, context));
        
        properties.clear();
        properties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, "components/page");
        assertEquals(null, JcrResourceProvider.getNodeType(properties, context));
        
        properties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, "nt:unstructured");
        assertEquals("nt:unstructured", JcrResourceProvider.getNodeType(properties, context));
        
        properties.put(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY, "undefined:nodetype");
        assertEquals(null, JcrResourceProvider.getNodeType(properties, context));
    }
    
    @Test(expected=PersistenceException.class)
    public void createWithNullPath() throws PersistenceException {
        jcrResourceProvider.create(null, null, null);
    }
    
    @Test
    public void createResourceBelowRoot() throws PersistenceException, RepositoryException {
        
        Map<String,Object> properties = new HashMap<>();
        properties.put(JcrConstants.JCR_PRIMARYTYPE, "nt:file");
        
        ResolveContext<JcrProviderState> context = mock(ResolveContext.class);
        JcrProviderState jcrProviderState = mock(JcrProviderState.class);
        when(context.getProviderState()).thenReturn(jcrProviderState);
        when(jcrProviderState.getSession()).thenReturn(session);
        
        JcrNodeResource child = (JcrNodeResource) jcrResourceProvider.create(context, "/childnode", properties);
        assertNotNull(child);
        assertEquals("/childnode", child.getPath());
        assertEquals("nt:file", child.getItem().getPrimaryNodeType().getName());
        
        properties.put("jcr:createdBy", "user");
        JcrNodeResource grandchild = (JcrNodeResource) jcrResourceProvider.create(context, "/childnode/grandchild", properties);
        assertNotNull(grandchild);
        assertEquals("/childnode/grandchild", grandchild.getPath());
        assertEquals("admin",grandchild.getItem().getProperty("jcr:createdBy").getString());
        
    }
    

    private static void assertResources(Resource... resources) {
        for (Resource r : resources) {
            assertNotNull(r);
            assertTrue(r instanceof JcrNodeResource);
        }
    }

    private static void assertParent(Resource parent, String expectedPath) {
        assertNotNull(parent);
        assertTrue(parent instanceof JcrNodeResource);
        assertEquals(expectedPath, parent.getPath());
    }
}


