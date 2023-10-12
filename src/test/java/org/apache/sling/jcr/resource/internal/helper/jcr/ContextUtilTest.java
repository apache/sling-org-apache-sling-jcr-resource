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
package org.apache.sling.jcr.resource.internal.helper.jcr;

import org.apache.sling.jcr.resource.internal.HelperData;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.jcr.Session;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class ContextUtilTest {
    
    private JcrProviderState state;
    private ResolveContext<JcrProviderState> ctx;
    
    @Before
    public void before() {
        state = mock(JcrProviderState.class);
        ctx = when(mock(ResolveContext.class).getProviderState()).thenReturn(state).getMock();
    }
    
    @After
    public void after() {
        reset(state, ctx);
    }
    
    @Test
    public void testGetHelperData() {
        HelperData hd = new HelperData(new AtomicReference<>(), new AtomicReference<>());
        when(state.getHelperData()).thenReturn(hd);

        assertSame(hd, ContextUtil.getHelperData(ctx));

        verify(state).getHelperData();
        verify(ctx).getProviderState();
        verifyNoMoreInteractions(ctx, state);
    }

    @Test
    public void testGetJcrItemResourceFactory() {
        JcrItemResourceFactory rf = mock(JcrItemResourceFactory.class);
        when(state.getResourceFactory()).thenReturn(rf);

        assertSame(rf, ContextUtil.getResourceFactory(ctx));

        verify(state).getResourceFactory();
        verify(ctx).getProviderState();
        verifyNoMoreInteractions(ctx, state);
    }

    @Test
    public void testGetSession() {
        Session session = mock(Session.class);
        when(state.getSession()).thenReturn(session);

        assertSame(session, ContextUtil.getSession(ctx));
        
        verify(state).getSession();
        verify(ctx).getProviderState();
        verifyNoMoreInteractions(ctx, state);
    }

    @Test
    public void testNullProviderState() {
        when(ctx.getProviderState()).thenReturn(null);
        
        try {
            ContextUtil.getHelperData(ctx);
            fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
            // success
        }

        try {
            ContextUtil.getResourceFactory(ctx);
            fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
            // success
        }

        try {
            ContextUtil.getSession(ctx);
            fail("IllegalStateException expected");
        } catch (IllegalStateException e) {
            // success
        }
        
        verify(ctx, times(3)).getProviderState();
        verifyNoMoreInteractions(ctx);
        verifyNoMoreInteractions(state);
    }

}