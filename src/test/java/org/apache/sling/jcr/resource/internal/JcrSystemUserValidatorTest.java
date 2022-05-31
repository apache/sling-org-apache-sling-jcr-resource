/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.jcr.resource.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Collections;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Group;
import org.apache.jackrabbit.api.security.user.User;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JcrSystemUserValidatorTest {

    private static final String GROUP_ADMINISTRATORS = "administrators";
    private static final String SYSTEM_USER_ID = "test-system-user";
    private JcrSystemUserValidator jcrSystemUserValidator;

    private JackrabbitSession session;
    private Group group;
    private User systemUser;

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    @Before
    public void setUp() throws IllegalArgumentException, IllegalAccessException, RepositoryException,
            NoSuchFieldException, SecurityException {
        jcrSystemUserValidator = new JcrSystemUserValidator();
        final Field repositoryField = jcrSystemUserValidator.getClass().getDeclaredField("repository");
        repositoryField.setAccessible(true);

        final SlingRepository repository = context.getService(SlingRepository.class);
        assertEquals("Apache Jackrabbit Oak", repository.getDescriptor("jcr.repository.name"));
        repositoryField.set(jcrSystemUserValidator, repository);

        session = (JackrabbitSession) context.resourceResolver().adaptTo(Session.class);
        UserManager userManager = session.getUserManager();
        group = userManager.createGroup(GROUP_ADMINISTRATORS);
        systemUser = userManager.createSystemUser(SYSTEM_USER_ID, null);
        if (session.hasPendingChanges()) {
            session.save();
        }
    }

    @After
    public void after() throws Exception {
        systemUser.remove();
        group.remove();
        session.save();
    }

    private void setAllowOnlySystemUsers(boolean allowOnlySystemUsers) {
        final JcrSystemUserValidator.Config config = new JcrSystemUserValidator.Config() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return null;
            }

            @Override
            public boolean allow_only_system_user() {
                return allowOnlySystemUsers;
            }

        };
        jcrSystemUserValidator.activate(config);
    }

    @Test
    public void testIsValidWithEnforcementOfSystemUsersEnabled() throws Exception {
        setAllowOnlySystemUsers(true);

        //testing null user
        assertFalse(jcrSystemUserValidator.isValid((String) null, null, null));
        //testing not existing user     
        assertFalse(jcrSystemUserValidator.isValid("notExisting", null, null));
        //administrators group is not a valid user  (also not a system user)
        assertFalse(jcrSystemUserValidator.isValid(group.getID(), null, null));
        // systemUser is valid
        assertTrue(jcrSystemUserValidator.isValid(systemUser.getID(), null, null));
    }

    @Test
    public void testIsValidPrincipalNamesWithEnforcementOfSystemUsersEnabled() throws Exception {
        setAllowOnlySystemUsers(true);

        //testing null principal names
        assertFalse(jcrSystemUserValidator.isValid((Iterable<String>) null, null, null));
        //testing not existing user
        assertFalse(jcrSystemUserValidator.isValid(Collections.singleton("notExisting"), null, null));
        //administrators group is not a valid user  (also not a system user)
        assertFalse(jcrSystemUserValidator.isValid(Collections.singleton(group.getPrincipal().getName()), null, null));
        // systemUser is valid
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(systemUser.getPrincipal().getName()), null, null));
    }

    @Test
    public void testIsValidWithEnforcementOfSystemUsersDisabled() throws Exception {
        setAllowOnlySystemUsers(false);

        //testing null user
        assertFalse(jcrSystemUserValidator.isValid((String) null, null, null));
        //testing not existing user (is considered valid here)
        assertTrue(jcrSystemUserValidator.isValid("notExisting", null, null));
        // administrators group is not a user at all (but considered valid)
        assertTrue(jcrSystemUserValidator.isValid(group.getID(), null, null));
        // systemUser is valid
        assertTrue(jcrSystemUserValidator.isValid(systemUser.getID(), null, null));
    }

    @Test
    public void testIsValidPrincipalNamesWithEnforcementOfSystemUsersDisabled() throws Exception {
        setAllowOnlySystemUsers(false);

        //testing null principal names
        assertFalse(jcrSystemUserValidator.isValid((Iterable<String>) null, null, null));
        //testing not existing user (is considered valid here)
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton("notExisting"), null, null));
        // administrators group is not a user at all (but considered valid)
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(group.getPrincipal().getName()), null, null));
        // systemUser is valid
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(systemUser.getPrincipal().getName()), null, null));
    }

    @Test
    public void testIsValidIdTwice() throws Exception {
        setAllowOnlySystemUsers(true);

        // Validation information is cached internally - need to test twice
        // to activate all code paths
        assertTrue(jcrSystemUserValidator.isValid(systemUser.getID(), null, null));
        assertTrue(jcrSystemUserValidator.isValid(systemUser.getID(), null, null));
    }

    @Test
    public void testIsValidPrincipalNameTwice() throws Exception {
        setAllowOnlySystemUsers(true);

        // Validation information is cached internally - need to test twice
        // to activate all code paths
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(systemUser.getPrincipal().getName()), null, null));
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(systemUser.getPrincipal().getName()), null, null));
    }
}
