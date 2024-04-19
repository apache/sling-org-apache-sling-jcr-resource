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

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import javax.jcr.GuestCredentials;
import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.security.Privilege;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.commons.jackrabbit.authorization.AccessControlUtils;
import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.spi.security.principal.EveryonePrincipal;
import org.apache.sling.jcr.resource.internal.HelperData;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JcrItemResourceFactoryTest extends SlingRepositoryTestBase {

    public static final String EXISTING_NODE_PATH = "/existing";
    public static final String NON_EXISTING_NODE_PATH = "/nonexisting";
    public static final String NON_ABSOLUTE_PATH = "invalidpath";
    public static final String REFERENCEABLE_NODE_PATH = "/referenceable";

    private Node node;
    private Node referenceableNode;
    private Session nonJackrabbitSession;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        final Session session = getSession();
        node = JcrUtils.getOrCreateByPath(EXISTING_NODE_PATH, "nt:unstructured", session);
        referenceableNode = JcrUtils.getOrCreateByPath(REFERENCEABLE_NODE_PATH, "nt:unstructured", session);
        referenceableNode.addMixin(JcrConstants.MIX_REFERENCEABLE);
        session.save();

        nonJackrabbitSession = (Session) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Session.class},
                (proxy, method, args) -> method.invoke(session, args));

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
        HelperData helper = new HelperData(new AtomicReference<>(), new AtomicReference<>());
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
        compareGetParentOrNull(session, EXISTING_NODE_PATH + "/" + JcrConstants.JCR_PRIMARYTYPE, false);
        compareGetParentOrNull(nonJackrabbitSession, EXISTING_NODE_PATH + "/" + JcrConstants.JCR_PRIMARYTYPE, false);
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

    public void testGetParentOrNullNonExistingParentNode() throws RepositoryException {
        Session s = mock(Session.class);
        when(s.getItem(EXISTING_NODE_PATH)).thenReturn(nonJackrabbitSession.getItem(EXISTING_NODE_PATH));
        when(s.nodeExists(PathUtils.getParentPath(EXISTING_NODE_PATH))).thenReturn(false);
        compareGetParentOrNull(s, EXISTING_NODE_PATH, true);
    }

    public void testGetParentOrNullWithException() throws RepositoryException {
        Session s = mock(Session.class);
        when(s.getItem(EXISTING_NODE_PATH)).thenReturn(nonJackrabbitSession.getItem(EXISTING_NODE_PATH));
        when(s.nodeExists(PathUtils.getParentPath(EXISTING_NODE_PATH))).thenThrow(new RepositoryException());
        compareGetParentOrNull(s, EXISTING_NODE_PATH, true);
    }

    public void testGetNodeByIdentifier() throws RepositoryException {
        HelperData helper = new HelperData(new AtomicReference<>(), new AtomicReference<>());
        String identifier = referenceableNode.getIdentifier();
        String uuid = referenceableNode.getProperty(JcrConstants.JCR_UUID).getString();
        assertEquals(identifier, uuid);
        Item referenceableItem =
                new JcrItemResourceFactory(session, helper).getItemOrNull(JcrItemResourceFactory.SEARCH_BY_ID_PREFIX + referenceableNode.getIdentifier());
        assertNotNull(referenceableItem);
        assertTrue(referenceableItem.isNode());
        assertEquals(REFERENCEABLE_NODE_PATH, referenceableItem.getPath());

        // the node identifier in this case is the path
        Item nodeItem = new JcrItemResourceFactory(session, helper).getItemOrNull(JcrItemResourceFactory.SEARCH_BY_ID_PREFIX + node.getIdentifier());
        assertNotNull(nodeItem);
        assertTrue(nodeItem.isNode());
        assertEquals(EXISTING_NODE_PATH, nodeItem.getPath());
    }

    private void compareGetParentOrNull(Session s, String path, boolean nullExpected) throws RepositoryException {
        HelperData helper = new HelperData(new AtomicReference<>(), new AtomicReference<>());

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
