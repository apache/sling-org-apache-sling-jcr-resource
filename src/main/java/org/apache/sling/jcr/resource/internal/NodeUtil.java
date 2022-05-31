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

import static javax.jcr.Property.JCR_CONTENT;
import static javax.jcr.Property.JCR_DATA;
import static javax.jcr.Property.JCR_FROZEN_PRIMARY_TYPE;
import static javax.jcr.nodetype.NodeType.NT_FILE;
import static javax.jcr.nodetype.NodeType.NT_FROZEN_NODE;
import static javax.jcr.nodetype.NodeType.NT_LINKED_FILE;

import java.util.HashSet;
import java.util.Set;

import javax.jcr.Item;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

import org.jetbrains.annotations.NotNull;

public abstract class NodeUtil {

    /**
     * Update the mixin node types
     *
     * @param node the node
     * @param mixinTypes the mixins
     * @throws RepositoryException if the repository's namespaced prefixes cannot be retrieved
     */
    public static void handleMixinTypes(final Node node, final String[] mixinTypes) throws RepositoryException {
        final Set<String> newTypes = new HashSet<String>();
        if (mixinTypes != null) {
            for (final String value : mixinTypes) {
                newTypes.add(value);
            }
        }
        final Set<String> oldTypes = new HashSet<String>();
        for (final NodeType mixinType : node.getMixinNodeTypes()) {
            oldTypes.add(mixinType.getName());
        }
        for (final String name : oldTypes) {
            if (!newTypes.contains(name)) {
                node.removeMixin(name);
            } else {
                newTypes.remove(name);
            }
        }
        for (final String name : newTypes) {
            node.addMixin(name);
        }
    }

    /**
     * Returns the primary property of the given node. For {@code nt:file} nodes this is a property of the child node {@code jcr:content}.
     * In case the node has a {@code jcr:data} property it is returned, otherwise the node's primary item as specified by its node type recursively until a property is found .
     * 
     * @param node the node for which to return the primary property
     * @return the primary property of the given node
     * @throws ItemNotFoundException in case the given node does neither have a {@code jcr:data} property nor a primary property given through its node type
     * @throws RepositoryException in case some exception occurs
     */
    public static @NotNull Property getPrimaryProperty(@NotNull Node node) throws RepositoryException {
        // find the content node: for nt:file it is jcr:content
        // otherwise it is the node of this resource
        Node content = (node.isNodeType(NT_FILE) ||
                (node.isNodeType(NT_FROZEN_NODE) &&
                        node.getProperty(JCR_FROZEN_PRIMARY_TYPE).getString().equals(NT_FILE)))
                ? node.getNode(JCR_CONTENT)
                : node.isNodeType(NT_LINKED_FILE) ? node.getProperty(JCR_CONTENT).getNode() : node;
        Property data;
        // if the node has a jcr:data property, use that property
        if (content.hasProperty(JCR_DATA)) {
            data = content.getProperty(JCR_DATA);
        } else {
            // otherwise try to follow default item trail
            Item item = content.getPrimaryItem();
            while (item.isNode()) {
                item = ((Node) item).getPrimaryItem();
            }
            data = (Property) item;
        }
        return data;
    }
}
