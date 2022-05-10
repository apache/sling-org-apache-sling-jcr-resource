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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import javax.jcr.GuestCredentials;
import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.oak.spi.security.user.UserConstants;
import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.jcr.resource.internal.HelperData;

public class JcrItemResourceFactoryTest extends SlingRepositoryTestBase {

    public static final String EXISTING_NODE_PATH = "/existing";
    public static final String NON_EXISTING_NODE_PATH = "/nonexisting";
    public static final String NON_ABSOLUTE_PATH = "invalidpath";

    private Node node;
    private Session nonJackrabbitSession;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Session session = getSession();
        node = JcrUtils.getOrCreateByPath(EXISTING_NODE_PATH, "nt:unstructured", session);
        session.save();

        nonJackrabbitSession = (Session) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Session.class},
                new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return method.invoke(session, args);
            }
        });

        AccessControlUtils.allow(node, EveryonePrincipal.NAME, Privilege.JCR_READ);
        session.save();
    }

    @Override
    protected void tearDown() throws Exception {
        node.remove();
        session.save();
        super.tearDown();
    }

    // The following tests ensure that the behaviour between itemExists & getItem and Jackrabbits getItemOrNull is
    // the same

    public void testGetItemOrNullExistingItem() throws RepositoryException {
        compareGetItemOrNull(EXISTING_NODE_PATH, EXISTING_NODE_PATH);
    }

    public void testGetItemOrNullNonExistingItem() throws RepositoryException {
        compareGetItemOrNull(NON_EXISTING_NODE_PATH, null);
    }

    public void testGetItemOrNullNonAbsolutePath() throws RepositoryException {
        compareGetItemOrNull(NON_ABSOLUTE_PATH, null);
    }

    public void testGetItemOrNullEmptyPath() throws RepositoryException {
        compareGetItemOrNull("", null);
    }

    private void compareGetItemOrNull(String path, String expectedPath) throws RepositoryException {
        HelperData helper = new HelperData(new AtomicReference<DynamicClassLoaderManager>(), new AtomicReference<URIProvider[]>());
        Item item1 = new JcrItemResourceFactory(session, helper).getItemOrNull(path);
        Item item2 = new JcrItemResourceFactory(nonJackrabbitSession, helper).getItemOrNull(path);
        if (expectedPath == null) {
            assertNull(item1);
            assertNull(item2);
        } else {
            assertNotNull(item1);
            assertEquals(expectedPath, item1.getPath());
            assertNotNull(item2);
            assertEquals(expectedPath, item2.getPath());
        }
    }
    
    public void testGetParentOrNullExistingNode() throws RepositoryException {
        compareGetParentOrNull(session, EXISTING_NODE_PATH, false);
        compareGetParentOrNull(nonJackrabbitSession, EXISTING_NODE_PATH, false);
    }

    public void testGetParentOrNullExistingProperty() throws RepositoryException {
        compareGetParentOrNull(session,EXISTING_NODE_PATH + "/" + JcrConstants.JCR_PRIMARYTYPE, false);
        compareGetParentOrNull(nonJackrabbitSession,EXISTING_NODE_PATH + "/" + JcrConstants.JCR_PRIMARYTYPE, false);
    }
    
    public void testGetParentOrNullNonAccessibleParent() throws RepositoryException {
        Session guestSession = null;
        try {
            guestSession = session.getRepository().login(new GuestCredentials());
            compareGetParentOrNull(guestSession, EXISTING_NODE_PATH, true);
        } finally {
            if (guestSession != null) {
                guestSession.logout();
            }
        }
    }

    private void compareGetParentOrNull(Session s, String path, boolean nullExpected) throws RepositoryException {
        HelperData helper = new HelperData(new AtomicReference<DynamicClassLoaderManager>(), new AtomicReference<URIProvider[]>());

        String parentPath = PathUtils.getParentPath(path);
        Node parent = new JcrItemResourceFactory(s, helper).getParentOrNull(s.getItem(path), parentPath);
        if (nullExpected) {
            assertNull(parent);
        } else {
            assertNotNull(parent);
            assertEquals(parentPath, parent.getPath());
        }
    }
}
