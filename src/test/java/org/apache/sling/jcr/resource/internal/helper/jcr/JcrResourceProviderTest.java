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

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.jcr.resource.internal.HelperData;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.mockito.Mockito;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

import static javax.jcr.nodetype.NodeType.NT_UNSTRUCTURED;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class JcrResourceProviderTest extends SlingRepositoryTestBase {

    JcrResourceProvider jcrResourceProvider;
    Session session;

    @Override
    protected void setUp() throws Exception {
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
    protected void tearDown() throws Exception {
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
        return new JcrProviderState(session, new HelperData(null, null), false);
    }
    
    private @NotNull ResolveContext mockResolveContext() {
        ResolveContext ctx = mock(ResolveContext.class);
        when(ctx.getProviderState()).thenReturn(createProviderState());
        return ctx;
    }

    public void testAdaptTo_Principal() {
        ResolveContext ctx = mockResolveContext();
        Assert.assertNotNull(jcrResourceProvider.adaptTo(ctx, Principal.class));
    }

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
    
    public void testGetParentDifferentResource() {
        Resource r = mock(Resource.class);
        when(r.getPath()).thenReturn("/test/path");
        Resource parent = jcrResourceProvider.getParent(mockResolveContext(), r);
        assertFalse(parent instanceof JcrNodeResource);
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


