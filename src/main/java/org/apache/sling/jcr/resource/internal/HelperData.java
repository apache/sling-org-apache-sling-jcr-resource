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

import java.util.concurrent.atomic.AtomicReference;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This is a helper class used to pass several services/data to the resource
 * and value map implementations.
 */
public class HelperData {

    private static final URIProvider[] EMPTY_URLPROVIDERS = new URIProvider[0];

    private final AtomicReference<DynamicClassLoaderManager> dynamicClassLoaderManagerReference;
    private final AtomicReference<URIProvider[]> uriProviderReference;

    private volatile String[] namespacePrefixes;

    public HelperData(final @NotNull AtomicReference<DynamicClassLoaderManager> dynamicClassLoaderManagerReference,
                      @NotNull AtomicReference<URIProvider[]> uriProviderReference) {
        this.dynamicClassLoaderManagerReference = dynamicClassLoaderManagerReference;
        this.uriProviderReference = uriProviderReference;
    }

    public @NotNull String[] getNamespacePrefixes(final @NotNull Session session) throws RepositoryException {
        if (this.namespacePrefixes == null) {
            this.namespacePrefixes = session.getNamespacePrefixes();
        }
        return this.namespacePrefixes;
    }

    public @Nullable ClassLoader getDynamicClassLoader() {
        final DynamicClassLoaderManager dclm = this.dynamicClassLoaderManagerReference.get();
        if (dclm == null) {
            return null;
        }
        return dclm.getDynamicClassLoader();
    }

    public @NotNull URIProvider[] getURIProviders() {
        URIProvider[] ups = this.uriProviderReference.get();
        if (ups == null) {
            ups = EMPTY_URLPROVIDERS;
        }
        return ups;
    }
}
