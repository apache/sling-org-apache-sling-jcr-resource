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

import java.lang.reflect.Field;
import java.util.Collections;

import javax.jcr.Credentials;
import javax.jcr.LoginException;
import javax.jcr.NoSuchWorkspaceException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.naming.NamingException;

import org.apache.sling.commons.testing.jcr.RepositoryTestBase;
import org.apache.sling.jcr.api.SlingRepository;
import org.junit.Before;
import org.junit.Test;


public class JcrSystemUserValidatorTest extends RepositoryTestBase {
    
    private static final String GROUP_ADMINISTRATORS = "administrators";

    private JcrSystemUserValidator jcrSystemUserValidator;
    
    @Before
    public void setUp() throws IllegalArgumentException, IllegalAccessException, RepositoryException, NamingException, NoSuchFieldException, SecurityException {
        jcrSystemUserValidator = new JcrSystemUserValidator();
        Field repositoryField = jcrSystemUserValidator.getClass().getDeclaredField("repository");
        repositoryField.setAccessible(true);
        final SlingRepository delegate = getRepository();

        SlingRepository repository = new SlingRepository() {
            @Override
            public String getDefaultWorkspace() {
                return delegate.getDefaultWorkspace();
            }

            @Override
            public Session loginAdministrative(String s) throws LoginException, RepositoryException {
                return delegate.loginAdministrative(s);
            }

            @Override
            public Session loginService(String s, String s1) throws LoginException, RepositoryException {
                return delegate.loginAdministrative(s1);
            }

            @Override
            public String[] getDescriptorKeys() {
                return delegate.getDescriptorKeys();
            }

            @Override
            public boolean isStandardDescriptor(String s) {
                return delegate.isStandardDescriptor(s);
            }

            @Override
            public boolean isSingleValueDescriptor(String s) {
                return delegate.isSingleValueDescriptor(s);
            }

            @Override
            public Value getDescriptorValue(String s) {
                return delegate.getDescriptorValue(s);
            }

            @Override
            public Value[] getDescriptorValues(String s) {
                return delegate.getDescriptorValues(s);
            }

            @Override
            public String getDescriptor(String s) {
                return delegate.getDescriptor(s);
            }

            @Override
            public Session login(Credentials credentials, String s) throws LoginException, NoSuchWorkspaceException, RepositoryException {
                return delegate.login(credentials, s);
            }

            @Override
            public Session login(Credentials credentials) throws LoginException, RepositoryException {
                return delegate.login(credentials);
            }

            @Override
            public Session login(String s) throws LoginException, NoSuchWorkspaceException, RepositoryException {
                return delegate.login(s);
            }

            @Override
            public Session login() throws LoginException, RepositoryException {
                return delegate.login();
            }
        };
        repositoryField.set(jcrSystemUserValidator, repository);
    }
    
    @Test
    public void testIsValidWithEnforcementOfSystemUsersEnabled() throws Exception {
        Field allowOnlySystemUsersField = jcrSystemUserValidator.getClass().getDeclaredField("allowOnlySystemUsers");
        allowOnlySystemUsersField.setAccessible(true);
        allowOnlySystemUsersField.set(jcrSystemUserValidator, true);
        
        //testing null user
        assertFalse(jcrSystemUserValidator.isValid((String) null, null, null));
        //testing not existing user     
        assertFalse(jcrSystemUserValidator.isValid("notExisting", null, null));
        //administrators group is not a valid user  (also not a system user)
        assertFalse(jcrSystemUserValidator.isValid(GROUP_ADMINISTRATORS, null, null));
    }

    @Test
    public void testIsValidPrincipalNamesWithEnforcementOfSystemUsersEnabled() throws Exception {
        Field allowOnlySystemUsersField = jcrSystemUserValidator.getClass().getDeclaredField("allowOnlySystemUsers");
        allowOnlySystemUsersField.setAccessible(true);
        allowOnlySystemUsersField.set(jcrSystemUserValidator, true);

        //testing null principal names
        assertFalse(jcrSystemUserValidator.isValid((Iterable<String>) null, null, null));
        //testing not existing user
        assertFalse(jcrSystemUserValidator.isValid(Collections.singleton("notExisting"), null, null));
        //administrators group is not a valid user  (also not a system user)
        assertFalse(jcrSystemUserValidator.isValid(Collections.singleton(GROUP_ADMINISTRATORS), null, null));
    }
    
    @Test
    public void testIsValidWithEnforcementOfSystemUsersDisabled() throws Exception {
        Field allowOnlySystemUsersField = jcrSystemUserValidator.getClass().getDeclaredField("allowOnlySystemUsers");
        allowOnlySystemUsersField.setAccessible(true);
        allowOnlySystemUsersField.set(jcrSystemUserValidator, false);
        
        //testing null user
        assertFalse(jcrSystemUserValidator.isValid((String) null, null, null));
        //testing not existing user (is considered valid here)
        assertTrue(jcrSystemUserValidator.isValid("notExisting", null, null));
        // administrators group is not a user at all (but considered valid)
        assertTrue(jcrSystemUserValidator.isValid(GROUP_ADMINISTRATORS, null, null));
    }

    @Test
    public void testIsValidPrincipalNamesWithEnforcementOfSystemUsersDisabled() throws Exception {
        Field allowOnlySystemUsersField = jcrSystemUserValidator.getClass().getDeclaredField("allowOnlySystemUsers");
        allowOnlySystemUsersField.setAccessible(true);
        allowOnlySystemUsersField.set(jcrSystemUserValidator, false);

        //testing null principal names
        assertFalse(jcrSystemUserValidator.isValid((Iterable<String>) null, null, null));
        //testing not existing user (is considered valid here)
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton("notExisting"), null, null));
        // administrators group is not a user at all (but considered valid)
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(GROUP_ADMINISTRATORS), null, null));
    }
}
