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

import java.lang.reflect.Field;
import java.util.Collections;

import javax.jcr.RepositoryException;
import javax.naming.NamingException;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class JcrSystemUserValidatorTest {
    
    private static final String GROUP_ADMINISTRATORS = "administrators";
    private JcrSystemUserValidator jcrSystemUserValidator;

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);
    
    @Before
    public void setUp() throws IllegalArgumentException, IllegalAccessException, RepositoryException, NamingException, NoSuchFieldException, SecurityException {
        jcrSystemUserValidator = new JcrSystemUserValidator();
        final Field repositoryField = jcrSystemUserValidator.getClass().getDeclaredField("repository");
        repositoryField.setAccessible(true);

        final SlingRepository repository = context.getService(SlingRepository.class);
        assertEquals("Apache Jackrabbit Oak", repository.getDescriptor("jcr.repository.name"));
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
