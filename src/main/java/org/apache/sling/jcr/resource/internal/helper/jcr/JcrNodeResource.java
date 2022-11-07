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

import java.io.InputStream;
import java.net.URI;
import java.security.AccessControlException;
import java.util.Iterator;
import java.util.Map;

import javax.jcr.Item;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.sling.adapter.annotations.Adaptable;
import org.apache.sling.adapter.annotations.Adapter;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.resource.external.ExternalizableInputStream;
import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.jcr.resource.internal.HelperData;
import org.apache.sling.jcr.resource.internal.JcrModifiableValueMap;
import org.apache.sling.jcr.resource.internal.JcrValueMap;
import org.apache.sling.jcr.resource.internal.NodeUtil;
import org.apache.sling.jcr.resource.internal.helper.AccessLogger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A Resource that wraps a JCR Node */
@Adaptable(adaptableClass=Resource.class, adapters={
        @Adapter({Node.class, Map.class, Item.class, ValueMap.class}),
        @Adapter(value=InputStream.class, condition="If the resource is a JcrNodeResource and has a jcr:data property or is an nt:file node."),
        @Adapter(value=ExternalizableInputStream.class, condition="If the resource is a JcrNodeResource and has a jcr:data property or is an nt:file node, and can be read using a secure URL.")
})
class JcrNodeResource extends JcrItemResource<Node> { // this should be package private, see SLING-1414

    /** marker value for the resourceSuperType before trying to evaluate */
    private static final String UNSET_RESOURCE_SUPER_TYPE = "<unset>";

    /** default log */
    private static final Logger LOGGER = LoggerFactory.getLogger(JcrNodeResource.class);

    private String resourceType;

    private String resourceSuperType;

    private final HelperData helper;

    /**
     * Constructor
     * @param resourceResolver The resource resolver
     * @param path The path of the resource (lazily initialized if null)
     * @param node The Node underlying this resource
     * @param helper The helper providing access to dynamic class loader for loading serialized objects and uri provider reference.
     */
    public JcrNodeResource(final @NotNull ResourceResolver resourceResolver,
                           final @NotNull String path,
                           final @Nullable String version,
                           final @NotNull Node node,
                           final @NotNull HelperData helper) {
        super(resourceResolver, path, version, node, new JcrNodeResourceMetadata(node));
        this.helper = helper;
        this.resourceSuperType = UNSET_RESOURCE_SUPER_TYPE;
        AccessLogger.incrementUsage(resourceResolver, "newJcrNodeResource", path);
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceType()
     */
    @Override
    public @NotNull String getResourceType() {
        if (this.resourceType == null) {
            try {
                this.resourceType = getResourceTypeForNode(getNode());
            } catch (final RepositoryException e) {
                LOGGER.error("Unable to get resource type for node " + getNode(), e);
                this.resourceType = "<unknown resource type>";
            }
        }
        return resourceType;
    }

    /**
     * @see org.apache.sling.api.resource.Resource#getResourceSuperType()
     */
    @Override
    public String getResourceSuperType() {
        // Yes, this isn't how you're supposed to compare Strings, but this is intentional.
        if (resourceSuperType == UNSET_RESOURCE_SUPER_TYPE) {
            try {
                Property property = NodeUtil.getPropertyOrNull(getNode(), JcrResourceConstants.SLING_RESOURCE_SUPER_TYPE_PROPERTY);
                if (property != null) {
                    resourceSuperType = property.getValue().getString();
                }
            } catch (RepositoryException re) {
                // we ignore this
            }
            if (resourceSuperType == UNSET_RESOURCE_SUPER_TYPE) {
                resourceSuperType = null;
            }
        }
        return resourceSuperType;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <Type> Type adaptTo(Class<Type> type) {
        if (type == Node.class || type == Item.class) {
            return (Type) getNode(); // unchecked cast
        } else if (type == InputStream.class) {
            return (Type) getInputStream(); // unchecked cast
        } else if (type == Map.class || type == ValueMap.class) {
            AccessLogger.incrementUsage(this.getResourceResolver(), "adaptToValueMap", path);
            return (Type) new JcrValueMap(getNode(), this.helper);
        } else if (type == ModifiableValueMap.class) {
            // check write
            try {
                getNode().getSession().checkPermission(getPath(), "set_property");
                return (Type) new JcrModifiableValueMap(getNode(), this.helper);
            } catch (AccessControlException ace) {
                // the user has no write permission, cannot adapt
                LOGGER.debug(
                        "adaptTo(ModifiableValueMap): Cannot set properties on {}",
                        this);
            } catch (RepositoryException e) {
                // some other problem, cannot adapt
                LOGGER.debug(
                        "adaptTo(ModifiableValueMap): Unexpected problem for {}",
                        this);
            }
        }

        // fall back to default implementation
        return super.adaptTo(type);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + ", type=" + getResourceType()
                + ", superType=" + getResourceSuperType()
                + ", path=" + getPath();
    }

    // ---------- internal -----------------------------------------------------

    private @NotNull Node getNode() {
        return getItem();
    }

    /**
     * Returns a stream to the <em>jcr:data</em> property if the
     * {@link #getNode() node} is an <em>nt:file</em> or <em>nt:resource</em>
     * node. Otherwise returns <code>null</code>.
     */
    private @Nullable InputStream getInputStream() {
        // implement this for nt:file only
        final Node node = getNode();
        try {
            Property data;
            try {
                data = NodeUtil.getPrimaryProperty(node);
            } catch (ItemNotFoundException infe) {
                // we don't actually care, but log for completeness
                LOGGER.debug("getInputStream: No primary items for {}", this, infe);
                data = null;
            }

            URI uri = convertToPublicURI();
            if (uri != null) {
                return new JcrExternalizableInputStream(data, uri);
            }
            if (data != null) {
                return data.getBinary().getStream();
            }

        } catch (RepositoryException re) {
            LOGGER.error("getInputStream: Cannot get InputStream for " + this,
                    re);
        }

        // fallback to non-streamable resource
        return null;
    }

    /**
     * Ask each URIProvider in turn for a Public URI, and return the first
     * public URI provided.
     * @return a public URI.
     */
    private @Nullable URI convertToPublicURI() {
        for (URIProvider up : helper.getURIProviders()) {
            try {
                return up.toURI(this, URIProvider.Scope.EXTERNAL, URIProvider.Operation.READ);
            } catch (IllegalArgumentException e) {
                LOGGER.debug("{} declined toURI for resource '{}'", up.getClass(), getPath(), e);
            }
        }
        return null;
    }

    @Override
    @Nullable Iterator<Resource> listJcrChildren() {
        try {
        	NodeIterator iter = getNode().getNodes();
            if (iter.hasNext()) {
                return new JcrNodeResourceIterator(getResourceResolver(), path, version,
                        iter, this.helper, null);
            }
        } catch (final RepositoryException re) {
            LOGGER.error("listChildren: Cannot get children of " + this, re);
        }

        return null;
    }
}
