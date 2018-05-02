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

import javax.jcr.Session;

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
import org.mockito.Mockito;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;

@RunWith(Parameterized.class)
public class JcrResourceProviderSessionHandlingTest {

    private enum LoginStyle {USER, SESSION};

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

    private JcrResourceProvider jcrResourceProvider;
    private JcrProviderState jcrProviderState;

    @Before
    public void setUp() throws Exception {
        SlingRepository repo = RepositoryProvider.instance().getRepository();
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
