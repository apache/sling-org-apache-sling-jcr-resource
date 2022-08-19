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

import static org.junit.Assert.assertEquals;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import javax.jcr.Node;
import javax.jcr.Session;

import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class JcrValueMapTest {

    
    @Rule
    public SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);
    
    static String CALENDAR_STRING = "Fri Aug 19 16:02:37 GMT 2022";
    
    
    @Test
    @Ignore
    public void testCalendarConversionToString() throws Exception {
        Session session = context.resourceResolver().adaptTo(Session.class);
        Node n = session.getRootNode().addNode("test");
        
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
        cal.setTime(sdf.parse(CALENDAR_STRING));        
        n.setProperty("cal", cal);
        session.save();
        
        JcrValueMap vm = new JcrValueMap(n, Mockito.mock(HelperData.class));
        String value = vm.get("cal",String.class);
        assertEquals(CALENDAR_STRING, value);  
    }
    
    
}
