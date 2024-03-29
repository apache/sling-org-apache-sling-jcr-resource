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

import static javax.jcr.Property.JCR_CONTENT;
import static javax.jcr.Property.JCR_CREATED;
import static javax.jcr.Property.JCR_DATA;
import static javax.jcr.Property.JCR_ENCODING;
import static javax.jcr.Property.JCR_FROZEN_PRIMARY_TYPE;
import static javax.jcr.Property.JCR_LAST_MODIFIED;
import static javax.jcr.Property.JCR_MIMETYPE;
import static javax.jcr.nodetype.NodeType.NT_FILE;
import static javax.jcr.nodetype.NodeType.NT_FROZEN_NODE;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.apache.sling.api.resource.ResourceMetadata;
import org.apache.sling.jcr.resource.internal.NodeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class JcrNodeResourceMetadata extends ResourceMetadata {

    private static final long serialVersionUID = 1L;

    /** default log */
    private static final Logger LOGGER = LoggerFactory.getLogger(JcrNodeResourceMetadata.class);

    private final Node node;
    private Node contentNode;
    private boolean nodePromotionChecked = false;
    private long creationTime = -1;
    private boolean populated = false;

    public JcrNodeResourceMetadata(final @NotNull Node inNode) {
        this.node = inNode;
    }

    private @NotNull Node promoteNode() {
        // check stuff for nt:file nodes
        try {
            if ((!nodePromotionChecked) &&
                    (node.isNodeType(NT_FILE) ||
                            (node.isNodeType(NT_FROZEN_NODE) &&
                                    node.getProperty(JCR_FROZEN_PRIMARY_TYPE).getString().equals(NT_FILE)))) {
                creationTime = node.getProperty(JCR_CREATED).getLong();

                // continue our stuff with the jcr:content node
                // which might be nt:resource, which we support below
                // if the node is new, the content node might not exist yet
                if (!node.isNew() || node.hasNode(JCR_CONTENT)) {
                    contentNode = node.getNode(JCR_CONTENT);
                }
                nodePromotionChecked = true;
            }
        } catch (final RepositoryException re) {
            report(re);
        }
        return contentNode != null ? contentNode : node;
    }

    private void report(final RepositoryException re) {
        String nodePath = "<unknown node path>";
        try {
            nodePath = contentNode != null ? contentNode.getPath() : node.getPath();
        } catch (RepositoryException e) {
            // ignore
        }
        LOGGER.info("setMetaData: Problem extracting metadata information for {}", nodePath, re);
    }

    @Override
    public Object get(final Object key) {
        final Object result = super.get(key);
        if (result != null) {
            return result;
        }

        if (CREATION_TIME.equals(key)) {
            promoteNode();
            internalPut(CREATION_TIME, creationTime);
            return creationTime;
        } else if (CONTENT_TYPE.equals(key)) {
            String contentType = getPropertyString(promoteNode(), JCR_MIMETYPE);
            internalPut(CONTENT_TYPE, contentType);
            return contentType;
        } else if (CHARACTER_ENCODING.equals(key)) {
            String characterEncoding = getPropertyString(promoteNode(), JCR_ENCODING);
            internalPut(CHARACTER_ENCODING, characterEncoding);
            return characterEncoding;
        } else if (MODIFICATION_TIME.equals(key)) {
            long modificationTime = getPropertyLong(promoteNode(), JCR_LAST_MODIFIED);
            internalPut(MODIFICATION_TIME, modificationTime);
            return modificationTime;
        } else if (CONTENT_LENGTH.equals(key)) {
            long contentLength = getContentLength(promoteNode());
            internalPut(CONTENT_LENGTH, contentLength);
            return contentLength;
        }
        return null;
    }

    private @Nullable String getPropertyString(@NotNull Node node, @NotNull String propertyName) {
        try {
            Property property = NodeUtil.getPropertyOrNull(node, propertyName);
            if (property != null) {
                return property.getString();
            }
        } catch (final RepositoryException re) {
            report(re);
        }
        return null;
    }

    private long getPropertyLong(@NotNull Node node, @NotNull String propertyName) {
        try {
            Property prop = NodeUtil.getPropertyOrNull(node, propertyName);
            if (prop != null) {
                // We don't check node type, so the property might not be of type PropertyType.LONG
                try {
                    return prop.getLong();
                } catch (final ValueFormatException vfe) {
                    LOGGER.debug("Property {} cannot be converted to a long, ignored ({})", prop.getPath(), vfe);
                }
            }
        } catch (final RepositoryException re) {
            report(re);
        }
        return -1;
    }
    
    private long getContentLength(@NotNull Node node) {
        try {
            // if the node has a jcr:data property, use that property
            Property prop = NodeUtil.getPropertyOrNull(node, JCR_DATA);
            if (prop != null) {
                return JcrItemResource.getContentLength(prop);
            } else {
                // otherwise try to follow default item trail
                Item item = getPrimaryItem(node);
                while (item != null && item.isNode()) {
                    item = getPrimaryItem((Node) item);
                }
                if (item != null) {
                    final Property data = (Property) item;

                    // set the content length property as a side effect
                    // for resources which are not nt:file based and whose
                    // data is not in jcr:content/jcr:data this will lazily
                    // set the correct content length
                    return JcrItemResource.getContentLength(data);
                }
            }
        } catch (final RepositoryException re) {
            report(re);
        }
        return -1;
    }
    
    private static @Nullable Item getPrimaryItem(final @NotNull Node node) throws RepositoryException {
        String name = node.getPrimaryNodeType().getPrimaryItemName();
        if (name == null) {
            return null;
        }
        Property property = NodeUtil.getPropertyOrNull(node, name);
        if (property != null) {
            return property;
        }
        Node childNode = NodeUtil.getNodeOrNull(node, name);
        if (childNode != null) {
            return childNode;
        }
        return null;
    }

    private void populate() {
        if (populated) {
            return;
        }
        get(CREATION_TIME);
        get(CONTENT_TYPE);
        get(CHARACTER_ENCODING);
        get(MODIFICATION_TIME);
        get(CONTENT_LENGTH);
        populated = true;
    }

    @Override
    public @NotNull Set<Map.Entry<String, Object>> entrySet() {
        populate();
        return super.entrySet();
    }

    @Override
    public @NotNull Set<String> keySet() {
        populate();
        return super.keySet();
    }

    @Override
    public @NotNull Collection<Object> values() {
        populate();
        return super.values();
    }

    @Override
    public int size() {
        populate();
        return super.size();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean containsKey(final Object key) {
        if (super.containsKey(key)) {
            return true;
        }
        return CREATION_TIME.equals(key) ||
                CONTENT_TYPE.equals(key) ||
                CHARACTER_ENCODING.equals(key) ||
                MODIFICATION_TIME.equals(key) ||
                CONTENT_LENGTH.equals(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        if (super.containsValue(value)) {
            return true;
        }
        if (!populated) {
            populate();
            return super.containsValue(value);
        }
        return false;
    }
}
