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

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.sling.jcr.api.SlingRepository;

import junit.framework.TestCase;

public abstract class SlingRepositoryTestBase extends TestCase {
    
    protected Node testRoot;
    protected Session session;
    private int counter;

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (session != null) {
            session.logout();
        }
    }

    /** Return a JCR Session, initialized on demand */
    protected Session getSession() throws Exception {
        if (session == null) {
            session = getRepository().loginAdministrative(null);
        }
        return session;
    }
    
    /** Return a test root node, created on demand, with a unique path */
    protected Node getTestRootNode() throws Exception {
        if (testRoot == null) {
            final Node root = getSession().getRootNode();
            final Node classRoot = root.addNode(getClass().getSimpleName());
            testRoot = classRoot.addNode(System.currentTimeMillis() + "_" + (++counter));
        }
        return testRoot;
    }

    /** Return a Repository 
     * @throws Exception */
    protected SlingRepository getRepository() throws Exception {
        return SlingRepositoryProvider.getRepository();
    }

    


}
