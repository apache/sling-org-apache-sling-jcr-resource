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

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.junit.Assert;
import org.mockito.Mockito;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

public class JcrResourceProviderTest extends SlingRepositoryTestBase {

    JcrResourceProvider jcrResourceProvider;
    Session session;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // create the session
        session = getSession();
        Repository repo = getRepository();
        ComponentContext ctx = Mockito.mock(ComponentContext.class);
        Mockito.when(ctx.locateService(Mockito.anyString(), Mockito.any(ServiceReference.class))).thenReturn(repo);
        jcrResourceProvider = new JcrResourceProvider();
        jcrResourceProvider.activate(ctx);
    }

    @Override
    protected void tearDown() throws Exception {
        jcrResourceProvider.deactivate();
        super.tearDown();
    }

    public void testAdaptTo_Principal() {
        ResolveContext ctx = Mockito.mock(ResolveContext.class);
        Mockito.when(ctx.getProviderState()).thenReturn(new JcrProviderState(session, null, false));
        Assert.assertNotNull(jcrResourceProvider.adaptTo(ctx, Principal.class));
    }

    public void testOrderBefore() throws RepositoryException, PersistenceException {
        Node parentNode = session.getRootNode().addNode("parent", NodeType.NT_UNSTRUCTURED);
        parentNode.addNode("child1", NodeType.NT_UNSTRUCTURED);
        parentNode.addNode("child2", NodeType.NT_UNSTRUCTURED);
        parentNode.addNode("child3", NodeType.NT_UNSTRUCTURED);
        session.save();

        ResolveContext ctx = Mockito.mock(ResolveContext.class);
        JcrProviderState state = new JcrProviderState(session, null, false);
        Mockito.when(ctx.getProviderState()).thenReturn(state);
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
}


