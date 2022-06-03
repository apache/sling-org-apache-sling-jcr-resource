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
import org.jetbrains.annotations.NotNull;

import javax.jcr.Session;

final class ContextUtil {

    private ContextUtil() {}

    static @NotNull Session getSession(@NotNull ResolveContext<JcrProviderState> ctx) {
        return getProviderState(ctx).getSession();
    }

    static @NotNull JcrItemResourceFactory getResourceFactory(@NotNull ResolveContext<JcrProviderState> ctx) {
        return getProviderState(ctx).getResourceFactory();
    }

    static @NotNull HelperData getHelperData(@NotNull ResolveContext<JcrProviderState> ctx) {
        return getProviderState(ctx).getHelperData();
    }

    /**
     * As long as the provider is active there must be a state available. 
     * 
     * @param ctx the {@code ResolveContext}
     * @return the {@code JcrProviderState} associated with the given context.
     */
    private static @NotNull JcrProviderState getProviderState(@NotNull ResolveContext<JcrProviderState> ctx) {
        JcrProviderState state = ctx.getProviderState();
        if (state == null) {
            throw new IllegalStateException("Cannot retrieve JcrProviderState from ResolveContext.");
        }
        return state;
    }
}