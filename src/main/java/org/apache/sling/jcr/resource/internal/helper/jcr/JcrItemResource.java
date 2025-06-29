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

import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.apache.sling.api.resource.AbstractResource;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.jcr.resource.internal.NodeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class JcrItemResource<T extends Item> // this should be package private, see SLING-1414
    extends AbstractResource {

    /**
     * default log
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(JcrItemResource.class);

    private final ResourceResolver resourceResolver;

    protected final String path;

    protected final String version;

    private final T item;

    private final ResourceMetadata metadata;

    protected JcrItemResource(final @NotNull ResourceResolver resourceResolver,
                              final @NotNull String path,
                              final @Nullable String version,
                              final @NotNull T item,
                              final @NotNull ResourceMetadata metadata) {

        this.resourceResolver = resourceResolver;
        this.path = path;
        this.version = version;
        this.item = item;
        this.metadata = metadata;
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceResolver()
     */
    @Override
    public @NotNull ResourceResolver getResourceResolver() {
        return resourceResolver;
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getPath()
     */
    @Override
    public @NotNull String getPath() {
        if (version == null) {
            return path;
        } else if (version.contains(".")) {
            return String.format("%s;v='%s'", path, version);
        } else {
            return String.format("%s;v=%s", path, version);
        }
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceMetadata()
     */
    @Override
    public @NotNull ResourceMetadata getResourceMetadata() {
        return metadata;
    }

    /**
     * Get the underlying item. Depending on the concrete implementation either
     * a {@link javax.jcr.Node} or a {@link javax.jcr.Property}.
     *
     * @return a {@link javax.jcr.Node} or a {@link javax.jcr.Property}, depending
     *         on the implementation
     */
    protected @NotNull T getItem() {
        return item;
    }

    public static long getContentLength(final @NotNull Property property) throws RepositoryException {
        if (property.isMultiple()) {
            return -1;
        }

        long length = -1;
        if (property.getType() == PropertyType.BINARY) {
            // we're interested in the number of bytes, not the
            // number of characters
            try {
                length = property.getLength();
            } catch (final ValueFormatException vfe) {
                LOGGER.debug("Length of Property {} cannot be retrieved, ignored ({})", property.getPath(), vfe);
            }
        } else {
            length = property.getString().getBytes(StandardCharsets.UTF_8).length;
        }
        return length;
    }

    /**
     * Returns an iterator over the child resources or <code>null</code> if
     * there are none.
     */
    @Nullable abstract Iterator<Resource> listJcrChildren();

}
