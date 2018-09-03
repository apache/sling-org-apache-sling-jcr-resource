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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeThat;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.jcr.Credentials;
import javax.jcr.LoginException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.commons.testing.jcr.RepositoryProvider;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

@RunWith(Parameterized.class)
public class JcrResourceProviderSessionHandlingTest {

    private enum LoginStyle {USER, SESSION, SERVICE};

    private static final String AUTH_USER = "admin";
    private static final char[] AUTH_PASSWORD = "admin".toCharArray();
    private static final String SUDO_USER = "anonymous";

    @Parameters(name = "loginStyle= {0}, sudo = {1}, clone = {2}")
    public static List<Object[]> data() {

        LoginStyle[] loginStyles = LoginStyle.values();
        boolean[] sudoOptions = new boolean[] {false, true};
        boolean[] cloneOptions = new boolean[] {false, true};

        // Generate all possible combinations into data.
        List<Object[]> data = new ArrayList<>();
        Object[] dataPoint = new Object[3];
        for (LoginStyle loginStyle : loginStyles) {
            dataPoint[0] = loginStyle;
            for (boolean sudo : sudoOptions) {
                dataPoint[1] = sudo;
                for (boolean clone : cloneOptions) {
                    dataPoint[2] = clone;
                    data.add(dataPoint.clone());
                }
            }
        }
        return data;
    }

    @Parameter(0)
    public LoginStyle loginStyle;

    @Parameter(1)
    public boolean useSudo;

    @Parameter(2)
    public boolean doClone;

    // Session we're using when loginStyle == SESSION, null otherwise.
    private Session explicitSession;
    // TransientRepository has a bug that makes it ignore sessions created
    // by calling Session.impersonate(). To prevent the repo from closing
    // prematurely, have this dummy session open during the whole lifetime
    // of the test.
    private Session footInDoor;

    private JcrResourceProvider jcrResourceProvider;
    private JcrProviderState jcrProviderState;

    private static class SlingRepositoryWithDummyServiceUsers implements SlingRepository {
        private final SlingRepository wrapped;
        
        SlingRepositoryWithDummyServiceUsers(SlingRepository wrapped) {
            this.wrapped = wrapped;
        }
        
        @SuppressWarnings("deprecation")
        @Override
        public Session loginService(String subServiceName, String workspace)
                throws LoginException, RepositoryException {
            // just fake service logins by doing administrative logins instead
            return wrapped.loginAdministrative(workspace);
        }

        // the rest of the methods just delegate to wrapped

        @Override
        public String[] getDescriptorKeys() {
            return wrapped.getDescriptorKeys();
        }

        @Override
        public boolean isStandardDescriptor(String key) {
            return wrapped.isStandardDescriptor(key);
        }

        @Override
        public boolean isSingleValueDescriptor(String key) {
            return wrapped.isSingleValueDescriptor(key);
        }

        @Override
        public Value getDescriptorValue(String key) {
            return wrapped.getDescriptorValue(key);
        }

        @Override
        public Value[] getDescriptorValues(String key) {
            return wrapped.getDescriptorValues(key);
        }

        @Override
        public String getDescriptor(String key) {
            return wrapped.getDescriptor(key);
        }

        @Override
        public Session login(Credentials credentials, String workspaceName)
                throws LoginException, NoSuchWorkspaceException, RepositoryException {
            return wrapped.login(credentials, workspaceName);
        }

        @Override
        public Session login(Credentials credentials) throws LoginException, RepositoryException {
            return wrapped.login(credentials);
        }

        @Override
        public Session login(String workspaceName)
                throws LoginException, NoSuchWorkspaceException, RepositoryException {
            return wrapped.login(workspaceName);
        }

        @Override
        public Session login() throws LoginException, RepositoryException {
            return wrapped.login();
        }

        @Override
        public String getDefaultWorkspace() {
            return wrapped.getDefaultWorkspace();
        }

        @SuppressWarnings("deprecation")
        @Override
        public Session loginAdministrative(String workspace) throws LoginException, RepositoryException {
            return wrapped.loginAdministrative(workspace);
        }
        
    }

    @Before
    public void setUp() throws Exception {
        final SlingRepository repo = new SlingRepositoryWithDummyServiceUsers(RepositoryProvider.instance().getRepository());

        footInDoor = repo.loginAdministrative(null);

        Map<String, Object> authInfo = new HashMap<>();
        switch (loginStyle) {
        case USER:
            authInfo.put(ResourceResolverFactory.USER, AUTH_USER);
            authInfo.put(ResourceResolverFactory.PASSWORD, AUTH_PASSWORD);
            break;
        case SESSION:
            explicitSession = repo.loginAdministrative(null);
            authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, explicitSession);
            break;
        case SERVICE:
            Bundle mockBundle = mock(Bundle.class);
            BundleContext mockBundleContext = mock(BundleContext.class);
            when(mockBundle.getBundleContext()).thenReturn(mockBundleContext);
            when(mockBundleContext.getService(Matchers.<ServiceReference<Object>>any())).thenReturn(repo);
            authInfo.put(ResourceResolverFactory.SUBSERVICE, "dummy-service");
            authInfo.put(ResourceProvider.AUTH_SERVICE_BUNDLE, mockBundle);
            break;
        }

        if (useSudo) {
            authInfo.put(ResourceResolverFactory.USER_IMPERSONATION, SUDO_USER);
        }

        if (doClone) {
            authInfo.put(ResourceProvider.AUTH_CLONE, true);
        }

        ComponentContext ctx = mock(ComponentContext.class);
        when(ctx.locateService(anyString(), Mockito.<ServiceReference<Object>>any())).thenReturn(repo);

        jcrResourceProvider = new JcrResourceProvider();
        jcrResourceProvider.activate(ctx);

        jcrProviderState = jcrResourceProvider.authenticate(authInfo);
    }

    @After
    public void tearDown() throws Exception {

        // Some tests do a logout, so check for liveness before trying to log out.
        if (jcrProviderState.getSession().isLive()) {
            jcrResourceProvider.logout(jcrProviderState);
        }

        jcrResourceProvider.deactivate();

        if (explicitSession != null) {
            explicitSession.logout();
        }

        footInDoor.logout();
    }

    @Test
    public void returnedSessionIsLive() {
        assertTrue(jcrProviderState.getSession().isLive());
    }

    @Test
    public void sessionUsesCorrectUser() {
        String expectedUser = useSudo ? SUDO_USER : AUTH_USER;
        assertEquals(expectedUser, jcrProviderState.getSession().getUserID());
    }

    @Test
    public void explicitSessionNotClosedOnLogout() {
        assumeTrue(loginStyle == LoginStyle.SESSION);

        jcrResourceProvider.logout(jcrProviderState);

        assertTrue(explicitSession.isLive());
    }

    @Test
    public void sessionsDoNotLeak() {
        // This test is only valid if we either didn't pass an explicit session,
        // or the provider had to clone it. Sessions created by the provider
        // must be closed by the provider, or we have a session leak.
        assumeThat(jcrProviderState.getSession(), is(not(sameInstance(explicitSession))));

        jcrResourceProvider.logout(jcrProviderState);

        assertFalse(jcrProviderState.getSession().isLive());
    }

    @Test
    public void impersonatorIsReportedCorrectly() {
        assumeTrue(useSudo);

        @SuppressWarnings("unchecked")
        ResolveContext<JcrProviderState> mockContext = mock(ResolveContext.class);
        when(mockContext.getProviderState()).thenReturn(jcrProviderState);
        Object reportedImpersonator = jcrResourceProvider.getAttribute(mockContext, ResourceResolver.USER_IMPERSONATOR);

        assertEquals(AUTH_USER, reportedImpersonator);
    }

    @Test
    public void clonesAreIndependent() {
        assumeTrue(loginStyle == LoginStyle.SESSION && doClone);

        assertNotSame(explicitSession, jcrProviderState.getSession());
    }

}
