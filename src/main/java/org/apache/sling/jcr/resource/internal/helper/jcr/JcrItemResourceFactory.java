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

import java.util.LinkedList;
import java.util.Map;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.jcr.resource.internal.HelperData;
import org.apache.sling.jcr.resource.internal.NodeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JcrItemResourceFactory {

    /** Default logger */
    private static final Logger log = LoggerFactory.getLogger(JcrItemResourceFactory.class);

    private final Session session;

    private final HelperData helper;

    private final boolean isJackrabbit;

    public JcrItemResourceFactory(final @NotNull Session session, final @NotNull HelperData helper) {
        this.helper = helper;
        this.session = session;
        this.isJackrabbit = session instanceof JackrabbitSession;
    }

    /**
     * Creates a <code>Resource</code> instance for the item found at the
     * given path. If no item exists at that path or the item does not have
     * read-access for the session of this resolver, <code>null</code> is
     * returned.
     *
     * @param resourceResolver The resource resolver
     * @param resourcePath The absolute path
     * @param parent The parent resource or {@code null}
     * @param parameters The parameters or{@code null}
     * @return The <code>Resource</code> for the item at the given path.
     * @throws RepositoryException If an error occurrs accessing checking the
     *             item in the repository.
     */
    public @Nullable JcrItemResource<?> createResource(final @NotNull ResourceResolver resourceResolver, final @NotNull String resourcePath,
                                                       final @Nullable Resource parent, final @Nullable Map<String, String> parameters) throws RepositoryException {
        final String version;
        if (parameters != null && parameters.containsKey("v")) {
            version = parameters.get("v");
        } else {
            version = null;
        }
        
        Item item = getItem(resourcePath, parent, version);
        
        if (item == null) {
            log.debug("createResource: No JCR Item exists at path '{}'", resourcePath);
            return null;
        } else {
            final JcrItemResource<?> resource;
            if (item.isNode()) {
                log.debug("createResource: Found JCR Node Resource at path '{}'", resourcePath);
                resource = new JcrNodeResource(resourceResolver, resourcePath, version, (Node) item, helper);
            } else {
                log.debug("createResource: Found JCR Property Resource at path '{}'", resourcePath);
                resource = new JcrPropertyResource(resourceResolver, resourcePath, version, (Property) item);
            }
            resource.getResourceMetadata().setParameterMap(parameters);
            return resource;
        }
    }
    
    private @Nullable Item getItem(@NotNull String resourcePath, @Nullable Resource parent, @Nullable String versionSpecifier) throws RepositoryException {
        Node parentNode = null;
        String parentResourcePath = null;
        if (parent != null) {
            parentNode = parent.adaptTo(Node.class);
            parentResourcePath = parent.getPath();
        }
        
        Item item;
        if (parentNode != null && resourcePath.startsWith(parentResourcePath)) {
            String subPath = resourcePath.substring(parentResourcePath.length());
            if (!subPath.isEmpty() && subPath.charAt(0) == '/') {
                subPath = subPath.substring(1);
            }
            item = getSubitem(parentNode, subPath);
        } else {
            item = getItemOrNull(resourcePath);
        }

        if (item != null && versionSpecifier != null) {
            item = getHistoricItem(item, versionSpecifier);
        }
        return item;
    }
    
    private @Nullable Item getHistoricItem(Item item, String versionSpecifier) throws RepositoryException {
        Item currentItem = item;
        LinkedList<String> relPath = new LinkedList<>();
        Node version = null;
        while (!"/".equals(currentItem.getPath())) {
            if (isVersionable(currentItem)) {
                version = getFrozenNode((Node) currentItem, versionSpecifier);
                break;
            } else {
                relPath.addFirst(currentItem.getName());
                currentItem = currentItem.getParent();
            }
        }
        if (version != null) {
            return getSubitem(version, StringUtils.join(relPath.iterator(), '/'));
        }
        return null;
    }

    private static @Nullable Item getSubitem(@NotNull Node node, @NotNull String relPath) {
        try {
            if (relPath.isEmpty()) {
                return node;
            }
            Node childNode = NodeUtil.getNodeOrNull(node, relPath);
            if (childNode != null) {
                return childNode;
            }
            return NodeUtil.getPropertyOrNull(node, relPath);
        } catch (RepositoryException e) {
            log.debug("getSubitem: Can't get subitem {} of {}: {}", relPath, node, e.toString());
            return null;
        }
    }

    private @Nullable Node getFrozenNode(@NotNull Node node, @NotNull String versionSpecifier) throws RepositoryException {
        final VersionManager versionManager = session.getWorkspace().getVersionManager();
        final VersionHistory history = versionManager.getVersionHistory(node.getPath());
        if (history.hasVersionLabel(versionSpecifier)) {
            return history.getVersionByLabel(versionSpecifier).getFrozenNode();
        }
        if (history.hasNode(versionSpecifier)) {
            return history.getVersion(versionSpecifier).getFrozenNode();
        }
        return null;
    }

    private static boolean isVersionable(@NotNull Item item) throws RepositoryException {
        return item.isNode() && ((Node) item).isNodeType(NodeType.MIX_VERSIONABLE);
    }

    @Nullable Item getItemOrNull(@NotNull String path) {
        // Check first if the path is absolute. If it isn't, then we return null because the previous itemExists method,
        // which was replaced by this method, would have returned null as well (instead of throwing an exception).
        if (path.isEmpty() || path.charAt(0) != '/') {
            return null;
        }

        Item item = null;
        try {
            // Use fast getItemOrNull if session is a JackrabbitSession
            if (this.isJackrabbit) {
                item = ((JackrabbitSession) session).getItemOrNull(path);
            }
            // Fallback to slower itemExists & getItem pattern
            else if (session.itemExists(path)) {
                item = session.getItem(path);
            }
        } catch (RepositoryException e) {
            log.debug("Unable to access item at " + path + ", possibly invalid path", e);
        }

        return item;
    }

    @Nullable Node getParentOrNull(@NotNull Item child, @NotNull String parentPath) {
        Node parent = null;
        try {
            // Use fast getParentOrNull if session is a JackrabbitSession
            if (this.isJackrabbit) {
                parent = ((JackrabbitSession) session).getParentOrNull(child);
            } else if (session.nodeExists(parentPath)) {
                // Fallback to slower nodeExists & getNode pattern
                parent = session.getNode(parentPath);
            }
        } catch (RepositoryException e) {
            log.debug("Unable to access node at {}", parentPath, e);
        }

        return parent;
    }

}
