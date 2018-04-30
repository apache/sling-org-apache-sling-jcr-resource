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

import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.naming.NamingException;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.testing.jcr.RepositoryTestBase;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
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

    public void testLeakOnSudo() throws LoginException, RepositoryException, NamingException {
        Map<String, Object> authInfo = new HashMap<String, Object>();
        authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
        authInfo.put(ResourceResolverFactory.USER_IMPERSONATION, "anonymous");
        JcrProviderState providerState = jcrResourceProvider.authenticate(authInfo);
        Assert.assertNotEquals("Impersonation didn't start new session", session, providerState.getSession());
        jcrResourceProvider.logout(providerState);
        assertFalse("Impersonated session wasn't closed.", providerState.getSession().isLive());
    }

    public void testNoSessionSharing() throws LoginException {
        Map<String, Object> authInfo = new HashMap<String, Object>();
        authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);
        authInfo.put(ResourceProvider.AUTH_CLONE, true);
        JcrProviderState providerState = jcrResourceProvider.authenticate(authInfo);
        Assert.assertNotEquals("Cloned resolver didn't clone session.", session, providerState.getSession());
        jcrResourceProvider.logout(providerState);
    }
}


