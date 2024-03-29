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
package org.apache.sling.jcr.resource.internal;

import javax.jcr.Node;

import org.apache.sling.jcr.resource.internal.helper.jcr.SlingRepositoryTestBase;
import org.junit.Test;
import org.mockito.Mockito;

public class JcrValueMapTest extends SlingRepositoryTestBase {
    
    private Node rootNode;
    HelperData helperData;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        String rootPath = "/test_" + System.currentTimeMillis();
        rootNode = getSession().getRootNode().addNode(rootPath.substring(1),
                "nt:unstructured");
        rootNode.setProperty("string", "test");
        getSession().save();
        helperData = Mockito.mock(HelperData.class);
    }
    
    
    //  Tests with null as default value and class must pass, see https://issues.apache.org/jira/browse/SLING-11567
    
    @Test
    public void testGetWithDefaultValue() {
       JcrValueMap vm = new JcrValueMap(rootNode, helperData);
       assertEquals("test", vm.get("string","default"));
       assertEquals("default", vm.get("nonexistent","default"));
       assertNull(vm.get("nonexistent",null));
    }
    
    @Test
    public void testGetWithClass() {
        JcrValueMap vm = new JcrValueMap(rootNode, helperData);
        assertEquals("test", vm.get("string",String.class));
        assertEquals("test", vm.get("string",null)); 
    }
    
   

}
