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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Session;

import org.apache.felix.hc.api.Result.Status;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Rule;
import org.junit.Test;


public class JcrSystemUserValidatorTest {
    
    private static final String GROUP_ADMINISTRATORS = "administrators";
    private static final String SYSTEM_USER = "aSystemUser";
    private static final String NON_EXISTING_SYSTEM_USER = "nonExistingSystemUser";
    private static final String SYSTEM_USER_ADDED = "anotherSystemUser";
    
    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private JcrSystemUserValidator jcrSystemUserValidator;
    
    
    private void prepare(boolean allowOnlySystemUser) throws Exception {
        jcrSystemUserValidator = new JcrSystemUserValidator();
        
        Map<String,Object> props = new HashMap<>();
        props.put("allow.only.system.user",allowOnlySystemUser);
        context.registerInjectActivateService(jcrSystemUserValidator, props);
        addSystemUser(SYSTEM_USER);  
    }
    
    private void addSystemUser(String systemUserName) throws Exception  {
        JackrabbitSession jcrSession = (JackrabbitSession) context.resourceResolver().adaptTo(Session.class);
        UserManager um = jcrSession.getUserManager();
        um.createSystemUser(systemUserName, null);
        jcrSession.save();
    }
    
    
    @Test
    public void testIsValidWithEnforcementOfSystemUsersEnabled() throws Exception {
        
        prepare(true);
        //testing null user
        assertFalse(jcrSystemUserValidator.isValid((String) null, null, null));
        
        assertTrue(jcrSystemUserValidator.isValid(SYSTEM_USER, null, null));
        assertEquals(Status.OK,jcrSystemUserValidator.execute().getStatus());
             
        assertFalse(jcrSystemUserValidator.isValid(NON_EXISTING_SYSTEM_USER, null, null));
        assertEquals(Status.CRITICAL,jcrSystemUserValidator.execute().getStatus());
        //administrators group is not a valid user  (also not a system user)
        assertFalse(jcrSystemUserValidator.isValid(GROUP_ADMINISTRATORS, null, null));
    }

    @Test
    public void testIsValidPrincipalNamesWithEnforcementOfSystemUsersEnabled() throws Exception {
        prepare(true);
        //testing null principal names
        assertFalse(jcrSystemUserValidator.isValid((Iterable<String>) null, null, null));
        //testing not existing user
        assertFalse(jcrSystemUserValidator.isValid(Collections.singleton(NON_EXISTING_SYSTEM_USER), null, null));
        assertEquals(Status.CRITICAL,jcrSystemUserValidator.execute().getStatus());
        //administrators group is not a valid user  (also not a system user)
        assertFalse(jcrSystemUserValidator.isValid(Collections.singleton(GROUP_ADMINISTRATORS), null, null));
    }
    
    @Test
    public void testIsValidWithEnforcementOfSystemUsersDisabled() throws Exception {
        prepare(false);
        
        //testing null user
        assertFalse(jcrSystemUserValidator.isValid((String) null, null, null));
        //testing not existing user (is considered valid here)
        assertTrue(jcrSystemUserValidator.isValid(NON_EXISTING_SYSTEM_USER, null, null));
        assertTrue(jcrSystemUserValidator.execute().isOk());
        // administrators group is not a user at all (but considered valid)
        assertTrue(jcrSystemUserValidator.isValid(GROUP_ADMINISTRATORS, null, null));
    }

    @Test
    public void testIsValidPrincipalNamesWithEnforcementOfSystemUsersDisabled() throws Exception {
        prepare(false);

        //testing null principal names
        assertFalse(jcrSystemUserValidator.isValid((Iterable<String>) null, null, null));
        //testing not existing user (is considered valid here)
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(NON_EXISTING_SYSTEM_USER), null, null));
        assertTrue(jcrSystemUserValidator.execute().isOk());
        // administrators group is not a user at all (but considered valid)
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(GROUP_ADMINISTRATORS), null, null));
    }
    

    @Test
    public void testHealthcheckWithInstallingUser() throws Exception {
        
        prepare(true);
        assertFalse(jcrSystemUserValidator.isValid(Collections.singleton(SYSTEM_USER_ADDED), null, null));
        assertEquals(Status.CRITICAL,jcrSystemUserValidator.execute().getStatus());
        
        addSystemUser(SYSTEM_USER_ADDED);
        assertTrue(jcrSystemUserValidator.isValid(Collections.singleton(SYSTEM_USER_ADDED), null, null));
        assertEquals(Status.WARN,jcrSystemUserValidator.execute().getStatus());
            
    }
}
