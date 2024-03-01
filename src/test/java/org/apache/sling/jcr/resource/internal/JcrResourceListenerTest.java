/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.sling.jcr.resource.internal;

import static java.util.Collections.synchronizedList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.jcr.AccessDeniedException;
import javax.jcr.Credentials;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.path.PathSet;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.api.JcrResourceChange;
import org.apache.sling.jcr.resource.internal.helper.jcr.SlingRepositoryProvider;
import org.apache.sling.spi.resource.provider.ObservationReporter;
import org.apache.sling.spi.resource.provider.ObserverConfiguration;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test of JcrResourceListener.
 */
public class JcrResourceListenerTest {

    private JcrListenerBaseConfig config;

    private JcrResourceListener listener;

    private Session adminSession;

    private final String createdPath = "/test" + System.currentTimeMillis() + "-create";

    private final String movedPath = "/test" + System.currentTimeMillis() + "-moved";

    private final String pathToDelete = "/test" + System.currentTimeMillis() + "-delete";

    private final String pathToModify = "/test" + System.currentTimeMillis() + "-modify";

    private final List<ResourceChange> events = synchronizedList(new ArrayList<>());

    SlingRepository repository;

    @SuppressWarnings("deprecation")
    @Before
    public void setUp() throws Exception {
        repository = SlingRepositoryProvider.getRepository();
        this.adminSession = repository.loginAdministrative(null);
        registerListener("/");
    }

    private void registerListener(String paths) throws RepositoryException {
        unregisterListener();
        events.clear();
        ObservationReporter observationReporter = getObservationReporter(paths);
        this.config = new JcrListenerBaseConfig(observationReporter,
                new SlingRepository() {

                    @Override
                    public Session login(Credentials credentials, String workspaceName) throws RepositoryException {
                        return repository.login(credentials, workspaceName);
                    }

                    @Override
                    public Session login(String workspaceName) throws RepositoryException {
                        return repository.login(workspaceName);
                    }

                    @Override
                    public Session login(Credentials credentials) throws RepositoryException {
                        return repository.login(credentials);
                    }

                    @Override
                    public Session login() throws RepositoryException {
                        return repository.login();
                    }

                    @Override
                    public boolean isStandardDescriptor(String key) {
                        return repository.isStandardDescriptor(key);
                    }

                    @Override
                    public boolean isSingleValueDescriptor(String key) {
                        return repository.isSingleValueDescriptor(key);
                    }

                    @Override
                    public Value[] getDescriptorValues(String key) {
                        return repository.getDescriptorValues(key);
                    }

                    @Override
                    public Value getDescriptorValue(String key) {
                        return repository.getDescriptorValue(key);
                    }

                    @Override
                    public String[] getDescriptorKeys() {
                        return repository.getDescriptorKeys();
                    }

                    @Override
                    public String getDescriptor(String key) {
                        return repository.getDescriptor(key);
                    }

                    @Override
                    public Session loginService(String subServiceName, String workspace) throws RepositoryException {
                        return repository.loginAdministrative(workspace);
                    }

                    @Override
                    public Session loginAdministrative(String workspace) throws RepositoryException {
                        return repository.loginAdministrative(workspace);
                    }

                    @Override
                    public String getDefaultWorkspace() {
                        return repository.getDefaultWorkspace();
                    }
                });
        this.listener = new JcrResourceListener(this.config,
                observationReporter.getObserverConfigurations().get(0));
    }

    @After
    public void tearDown() throws AccessDeniedException, VersionException, LockException, ConstraintViolationException, RepositoryException {
        unregisterListener();
        if (adminSession.itemExists("/apps")) {
            adminSession.removeItem("/apps");
        }
        if (adminSession.itemExists("/apps2")) {
            adminSession.removeItem("/apps2");
        }
        if (adminSession != null) {
            adminSession.save();
            adminSession.logout();
            adminSession = null;
        }
    }

    private void unregisterListener() {
        if (listener != null) {
            listener.close();
            listener = null;
        }
        if (config != null) {
            config.close();
            config = null;
        }
    }

    @Test
    public void testSimpleOperations() throws Exception {
        this.adminSession.getWorkspace().getObservationManager().setUserData("testUserData");
        generateEvents(adminSession);
        assertEquals("Received: " + events, 5, events.size());
        final Set<String> addPaths = new HashSet<>();
        final Set<String> modifyPaths = new HashSet<>();
        final Set<String> removePaths = new HashSet<>();

        for (final ResourceChange event : events) {
            if (event.getType() == ChangeType.ADDED) {
                addPaths.add(event.getPath());
            } else if (event.getType() == ChangeType.CHANGED) {
                modifyPaths.add(event.getPath());
            } else if (event.getType() == ChangeType.REMOVED) {
                removePaths.add(event.getPath());
            } else {
                fail("Unexpected event: " + event);
            }
            assertNotNull(event.getUserId());
            assertTrue(event instanceof JcrResourceChange);
            assertEquals("testUserData", JcrResourceChange.class.cast(event).getUserData());
        }

        assertEquals(3, addPaths.size());
        assertTrue("Added set should contain " + createdPath, addPaths.contains(createdPath));
        assertTrue("Added set should contain " + pathToDelete, addPaths.contains(pathToDelete));
        assertTrue("Added set should contain " + pathToModify, addPaths.contains(pathToModify));

        assertEquals(1, modifyPaths.size());
        assertTrue("Modified set should contain " + pathToModify, modifyPaths.contains(pathToModify));

        assertEquals(1, removePaths.size());
        assertTrue("Removed set should contain " + pathToDelete, removePaths.contains(pathToDelete));
    }

    @Test
    public void testMoveOperationsInsideObservedPathOnLeafNode() throws RepositoryException, InterruptedException {
        createNode(adminSession, createdPath);
        adminSession.move(createdPath, movedPath);
        adminSession.save();
        Thread.sleep(3500);

        assertTrue("Events must contain \"added\" for path \"" + movedPath + "\"",
                events.stream().anyMatch(e -> e.getPath().equals(movedPath) && e.getType() == ChangeType.ADDED));
        assertTrue("Events must contain \"removed\" for path \"" + createdPath + "\"",
                events.stream().anyMatch(e -> e.getPath().equals(createdPath) && e.getType() == ChangeType.REMOVED));
    }

    @Test
    public void testMoveOperationsInsideObservedPath() throws RepositoryException, InterruptedException {
        createNode(adminSession, "/apps/test" + createdPath);
        // clear events for node creation
        Thread.sleep(2000);
        registerListener("/");
        adminSession.move("/apps", "/apps2");
        adminSession.save();
        Thread.sleep(3500);

        // 1 added + 1 removed event for roots
        assertEquals("Events must only contain 2 events but has " + events.toString(), 2, events.size());
        assertTrue("Events must contain \"added\" for path \"/apps2\"",
                events.stream().anyMatch(e -> e.getPath().equals("/apps2") && e.getType() == ChangeType.ADDED));
        assertTrue("Events must contain \"removed\" for path \"/apps\"",
                events.stream().anyMatch(e -> e.getPath().equals("/apps") && e.getType() == ChangeType.REMOVED));
    }

    @Test
    public void testMoveOperationsIntoObservedPath() throws RepositoryException, InterruptedException {
        registerListener("/apps");
        createNode(adminSession, "/apps2/test" + createdPath);
        adminSession.move("/apps2", "/apps");
        adminSession.save();
        Thread.sleep(3500);
        String expectedAddedPath = "/apps/test" + createdPath;
        assertTrue("Events must contain \"added\" for path \"" + expectedAddedPath + "\"",
                events.stream().anyMatch(e -> e.getPath().equals(expectedAddedPath) && e.getType() == ChangeType.ADDED));
        assertFalse("Events must not contain any \"removed\" events",
                events.stream().anyMatch(e -> e.getType() == ChangeType.REMOVED));
    }

    @Test
    public void testMoveOperationsOutOfObservedPath() throws RepositoryException, InterruptedException {
        createNode(adminSession, "/apps/test" + createdPath);
        Thread.sleep(2000);
        registerListener("/apps");
        adminSession.move("/apps", "/apps2");
        adminSession.save();
        Thread.sleep(3500);
        String expectedPath = "/apps";
        // 1 removed events for the moved root only
        assertEquals("Events must only contain 1 events but has " + events.toString(), 1, events.size());
        assertTrue("Events must contain \"removed\" for path \"" + expectedPath + "\"",
                events.stream().anyMatch(e -> e.getPath().equals(expectedPath) && e.getType() == ChangeType.REMOVED));
    }

    @Test
    public void testMoveOperationsIntoObservedPathWithGlobs() throws RepositoryException, InterruptedException {
        registerListener("glob:/*/test/**");
        createNode(adminSession, "/apps/test2" + createdPath);
        adminSession.move("/apps/test2", "/apps/test"); // move happens above observed path
        adminSession.save();
        Thread.sleep(3500);

        // only an added event for the root is received
        assertEquals("Events must only contain 1 event but has " + events.toString(), 1, events.size());
        assertTrue("Events must contain \"added\" for root path \"/apps/test\"",
                events.stream().anyMatch(e -> e.getPath().equals("/apps/test") && e.getType() == ChangeType.ADDED));
    }

    @Test
    public void testMoveOperationsOutOfObservedPathWithGlobs() throws RepositoryException, InterruptedException {
        createNode(adminSession, "/apps/test" + createdPath);
        Thread.sleep(2000);
        registerListener("glob:/*/test/**");
        adminSession.save();
        adminSession.move("/apps/test", "/apps/test2"); // move happens above observed path
        adminSession.save();
        Thread.sleep(3500);

        // 2 removed events for the whole subgraph below the observed path is received
        assertEquals("Events must only contain 2 events but has " + events.toString(), 2, events.size());
        assertTrue("Events must contain \"added\" for root path \"/apps/test\"",
                events.stream().anyMatch(e -> e.getPath().equals("/apps/test" + createdPath) && e.getType() == ChangeType.REMOVED));
    }

    @Test
    public void testOrderBeforeOperations() throws RepositoryException, InterruptedException {
        Node node = createNode(adminSession, createdPath);
        node.addNode("child1");
        node.addNode("child2");

        adminSession.save();
        Thread.sleep(2000);
        events.clear();
        // reorder child1 to appear after child2
        node.orderBefore("child1", null);

        adminSession.save();
        Thread.sleep(2000);

        // TODO: send events here
        assertEquals("Received: " + events, 0, events.size());
    }

    @Test
    public void testMultiplePaths() throws Exception {
        ObserverConfiguration observerConfig = new ObserverConfiguration() {

            @Override
            public boolean includeExternal() {
                return true;
            }

            @Override
            public PathSet getPaths() {
                return PathSet.fromStrings("/libs", "/apps");
            }

            @Override
            public PathSet getExcludedPaths() {
                return PathSet.fromPaths();
            }

            @Override
            public Set<ChangeType> getChangeTypes() {
                return EnumSet.allOf(ChangeType.class);
            }

            @Override
            public boolean matches(String path) {
                return this.getPaths().matches(path) != null;
            }

            @Override
            @NotNull
            public Set<String> getPropertyNamesHint() {
                return null;
            }
        };
        this.config.unregister(this.listener);
        this.listener = null;
        final Session session = this.adminSession;
        if (!session.nodeExists("/libs")) {
            createNode(session, "/libs");
        }
        if (!session.nodeExists("/apps")) {
            createNode(session, "/apps");
        }
        session.getNode("/libs").addNode("foo" + System.currentTimeMillis());
        session.getNode("/apps").addNode("foo" + System.currentTimeMillis());

        session.save();

        Thread.sleep(200);

        this.events.clear();

        try (final JcrResourceListener l = new JcrResourceListener(this.config, observerConfig)) {
            final String rootName = "test_" + System.currentTimeMillis();
            for (final String path : new String[]{"/libs", "/", "/apps", "/content"}) {
                final Node parent;
                if (!session.nodeExists(path)) {
                    parent = createNode(session, path);
                } else {
                    parent = session.getNode(path);
                }
                final Node node = parent.addNode(rootName, "nt:unstructured");
                session.save();

                node.setProperty("foo", "bar");
                session.save();

                node.remove();
                session.save();
            }
            System.out.println("Events = " + events);
            assertEquals("Received: " + events, 6, events.size());
            final Set<String> addPaths = new HashSet<>();
            final Set<String> modifyPaths = new HashSet<>();
            final Set<String> removePaths = new HashSet<>();

            for (final ResourceChange event : events) {
                if (event.getType() == ChangeType.ADDED) {
                    addPaths.add(event.getPath());
                } else if (event.getType() == ChangeType.CHANGED) {
                    modifyPaths.add(event.getPath());
                } else if (event.getType() == ChangeType.REMOVED) {
                    removePaths.add(event.getPath());
                } else {
                    fail("Unexpected event: " + event);
                }
                assertNotNull(event.getUserId());
            }
            assertEquals("Received: " + addPaths, 2, addPaths.size());
            assertTrue("Added set should contain /libs/" + rootName, addPaths.contains("/libs/" + rootName));
            assertTrue("Added set should contain /apps/" + rootName, addPaths.contains("/apps/" + rootName));

            assertEquals("Received: " + modifyPaths, 2, modifyPaths.size());
            assertTrue("Modified set should contain /libs/" + rootName, modifyPaths.contains("/libs/" + rootName));
            assertTrue("Modified set should contain /apps/" + rootName, modifyPaths.contains("/apps/" + rootName));

            assertEquals("Received: " + removePaths, 2, removePaths.size());
            assertTrue("Removed set should contain /libs/" + rootName, removePaths.contains("/libs/" + rootName));
            assertTrue("Removed set should contain /apps/" + rootName, removePaths.contains("/apps/" + rootName));
        }
    }

    private static Node createNode(final Session session, final String path) throws RepositoryException {
        Node n = JcrUtils.getOrCreateByPath(path, "nt:unstructured", session);
        session.save();
        return n;
    }

    private void generateEvents(Session session) throws Exception {
        // create the nodes
        createNode(session, createdPath);
        createNode(session, pathToModify);
        createNode(session, pathToDelete);

        Thread.sleep(1000);

        // modify
        final Node modified = session.getNode(pathToModify);
        modified.setProperty("foo", "bar");

        session.save();

        // delete
        final Node deleted = session.getNode(pathToDelete);
        deleted.remove();
        session.save();

        Thread.sleep(3500);
    }

    protected ObservationReporter getObservationReporter() {
        return new SimpleObservationReporter("/");
    }

    protected ObservationReporter getObservationReporter(String... paths) {
        return new SimpleObservationReporter(paths);
    }

    private class SimpleObservationReporter implements ObservationReporter {

        private final String[] paths;
        public SimpleObservationReporter(String... paths) {
            this.paths = paths;
        }
        @Override
        public void reportChanges(Iterable<ResourceChange> changes, boolean distribute) {
            for (ResourceChange c : changes) {
                events.add(c);
            }
        }

        @Override
        public @NotNull List<ObserverConfiguration> getObserverConfigurations() {
            ObserverConfiguration config = new ObserverConfiguration() {

                @Override
                public boolean includeExternal() {
                    return true;
                }

                @Override
                public PathSet getPaths() {
                    return PathSet.fromStrings(paths);
                }

                @Override
                public PathSet getExcludedPaths() {
                    return PathSet.fromPaths();
                }

                @Override
                public Set<ChangeType> getChangeTypes() {
                    return EnumSet.allOf(ChangeType.class);
                }

                @Override
                public boolean matches(String path) {
                    return true;
                }

                @Override
                public Set<String> getPropertyNamesHint() {
                    return new HashSet<>();
                }
            };
            return Collections.singletonList(config);
        }

        @Override
        public void reportChanges(@NotNull ObserverConfiguration config, @NotNull Iterable<ResourceChange> changes, boolean distribute) {
            this.reportChanges(changes, distribute);
        }
    }

}
