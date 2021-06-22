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

import org.apache.sling.api.resource.Resource;
import org.apache.sling.commons.testing.jcr.RepositoryTestBase;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.junit.Assert;
import org.mockito.Mockito;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

public class JcrResourceProviderTest extends RepositoryTestBase {

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

    public void testGetParent() throws RepositoryException {

        ResolveContext ctx = Mockito.mock(ResolveContext.class);
        ResourceContext rCtx = Mockito.mock(ResourceContext.class);
        Mockito.when(ctx.getProviderState()).thenReturn(new JcrProviderState(session, null, false));

        Node root = session.getRootNode();

        assertNotNull(root);

        Node nodeA = root.addNode("test-jcr-resource-provider-"+System.nanoTime()).addNode("node-a", NodeType.NT_UNSTRUCTURED);
        Node nodeB = nodeA.addNode("node-b", NodeType.NT_UNSTRUCTURED);
        Node nodeC = nodeB.addNode("node-c", NodeType.NT_UNSTRUCTURED);

        session.save();

        Resource resourceC = jcrResourceProvider.getResource(ctx, nodeC.getPath(), rCtx, null);
        assertNotNull(resourceC);

        Resource resourceB = jcrResourceProvider.getParent(ctx, resourceC);
        assertNotNull(resourceB);
        assertEquals(resourceB.getPath(), nodeB.getPath());

        Resource resourceA = jcrResourceProvider.getParent(ctx, resourceB);
        assertNotNull(resourceA);
        assertEquals(resourceA.getPath(), nodeA.getPath());
    }
}


