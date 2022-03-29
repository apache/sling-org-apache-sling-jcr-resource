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

import java.lang.annotation.Annotation;
import java.util.Iterator;

import javax.jcr.Node;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import javax.jcr.query.Query;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.jcr.resource.SimpleProviderContext;
import org.apache.sling.jcr.resource.internal.helper.jcr.JcrResourceProvider.Config;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

import com.google.common.collect.Iterators;

public class JcrResourceProviderQueryTest extends SlingRepositoryTestBase {

    Session session;
    private ComponentContext componentContext;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // create the session
        session = getSession();
        Repository repo = getRepository();
        componentContext = Mockito.mock(ComponentContext.class);
        Mockito.when(componentContext.locateService(Mockito.anyString(), Mockito.any(ServiceReference.class)))
                .thenReturn(repo);
    }

    private JcrResourceProvider getProvider(Config config) throws RepositoryException {
        JcrResourceProvider jcrResourceProvider = new JcrResourceProvider();
        jcrResourceProvider.activate(componentContext, config);
        final ProviderContext pcontext = new SimpleProviderContext();
        jcrResourceProvider.start(pcontext);
        return jcrResourceProvider;
    }

    private ResolveContext<JcrProviderState> getResolveContext() {
        ResolveContext<JcrProviderState> ctx = Mockito.mock(ResolveContext.class);
        JcrProviderState state = new JcrProviderState(session, null, false);
        Mockito.when(ctx.getProviderState()).thenReturn(state);
        return ctx;
    }

    @Test
    public void testQueryBelowLimit() throws RepositoryException {
        runTest("below-limit", new SimpleConfig(true, 100), 99, 99);
    }

    @Test
    public void testQueryAboveLimit() throws RepositoryException {
        runTest("above-limit", new SimpleConfig(true, 100), 200, 100);
    }

    @Test
    public void testDisabled() throws RepositoryException {
        runTest("disabled", new SimpleConfig(false, 100), 200, 200);
    }

    @Test
    public void testInvalid() throws RepositoryException {
        runTest("invalid", new SimpleConfig(true, 0), 200, 200);
    }

    private void runTest(String name, Config config, int create, int expected) throws RepositoryException {

        JcrResourceProvider jcrResourceProvider = null;
        try {
            jcrResourceProvider = getProvider(config);
            Node parentNode = session.getRootNode().addNode("parent-" + name, NodeType.NT_UNSTRUCTURED);
            for (int i = 0; i < create; i++) {
                parentNode.addNode("child" + i, NodeType.NT_UNSTRUCTURED);
            }
            session.save();
            ResolveContext<JcrProviderState> ctx = getResolveContext();
            Iterator<Resource> resources = jcrResourceProvider.getQueryLanguageProvider().findResources(ctx,
                    "SELECT * FROM [nt:unstructured] WHERE ISDESCENDANTNODE([" + parentNode.getPath() + "])",
                    Query.JCR_SQL2);
            assertEquals(expected, Iterators.size(resources));
        } finally {
            jcrResourceProvider.deactivate();
        }
    }

    private static class SimpleConfig implements JcrResourceProvider.Config {
        private final boolean enabled;
        private final long limit;

        public SimpleConfig(boolean enabled, long limit) {
            this.enabled = enabled;
            this.limit = limit;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return null;
        }

        @Override
        public boolean enable_query_limit() {
            return enabled;
        }

        @Override
        public long query_limit() {
            return this.limit;
        }

    }

}
