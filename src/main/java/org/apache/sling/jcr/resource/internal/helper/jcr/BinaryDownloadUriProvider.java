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

import java.net.URI;

import javax.jcr.Binary;
import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.api.binary.BinaryDownload;
import org.apache.jackrabbit.api.binary.BinaryDownloadOptions;
import org.apache.jackrabbit.api.binary.BinaryDownloadOptions.BinaryDownloadOptionsBuilder;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.jcr.resource.internal.NodeUtil;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.propertytypes.ServiceRanking;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;

/**
 * Provides URIs for direct binary read-access based on the Jackrabbit API {@link BinaryDownload}.
 * 
 * @see <a href="https://jackrabbit.apache.org/oak/docs/features/direct-binary-access.html">Oak Direct Binary Access</a>
 *
 */
@Component(service = URIProvider.class, configurationPolicy = ConfigurationPolicy.REQUIRE)
@ServiceRanking(-100)
@Designate(ocd = BinaryDownloadUriProvider.Configuration.class)
public class BinaryDownloadUriProvider implements URIProvider {

    enum ContentDisposition {
        INLINE,
        ATTACHMENT
    }

    @ObjectClassDefinition(
            name = "Apache Sling Binary Download URI Provider",
            description = "Provides URIs for resources containing a primary JCR binary property backed by a blob store allowing direct HTTP access")
    public static @interface Configuration {
        @AttributeDefinition(
                name = "Content-Disposition",
                description = "The content-disposition header to send when the binary is delivered via HTTP")
        ContentDisposition contentDisposition();
    }

    private final boolean isContentDispositionAttachment;

    @Activate
    public BinaryDownloadUriProvider(Configuration configuration) {
        this(configuration.contentDisposition() == ContentDisposition.ATTACHMENT);
    }

    BinaryDownloadUriProvider(boolean isContentDispositionAttachment) {
        this.isContentDispositionAttachment = isContentDispositionAttachment;
    }

    @Override
    public @NotNull URI toURI(@NotNull Resource resource, @NotNull Scope scope, @NotNull Operation operation) {
        if (!isRelevantScopeAndOperation(scope, operation)) {
            throw new IllegalArgumentException("This provider only provides URIs for 'READ' operations in scope 'PUBLIC' or 'EXTERNAL', but not for scope '" + scope + "' and operation '" + operation + "'");
        }
        Node node = resource.adaptTo(Node.class);
        if (node == null) {
            throw new IllegalArgumentException("This provider only provides URIs for node-based resources");
        }
        try {
            // get main property (probably containing binary data)
            Property primaryProperty = getPrimaryProperty(node);
            try {
                return getUriFromProperty(resource, node, primaryProperty);
            } catch (RepositoryException e) {
                throw new IllegalArgumentException("Error getting URI for property '" + primaryProperty.getPath() + "'", e);
            }
        } catch (ItemNotFoundException e) {
            throw new IllegalArgumentException("Node does not have a primary property", e);
        } catch (RepositoryException e) {
            throw new IllegalArgumentException("Error accessing primary property", e);
        }
    }

    protected @NotNull Property getPrimaryProperty(@NotNull Node node) throws RepositoryException {
        return NodeUtil.getPrimaryProperty(node);
    }

    private boolean isRelevantScopeAndOperation(@NotNull Scope scope, @NotNull Operation operation) {
       return ((Scope.PUBLIC.equals(scope) || Scope.EXTERNAL.equals(scope)) && Operation.READ.equals(operation));
    }

    private @NotNull URI getUriFromProperty(@NotNull Resource resource, @NotNull Node node, @NotNull Property binaryProperty) throws ValueFormatException, RepositoryException {
        Binary binary = binaryProperty.getBinary();
        if (!(binary instanceof BinaryDownload)) {
            binary.dispose();
            throw new IllegalArgumentException("The property " + binaryProperty.getPath() + " is not backed by a blob store allowing direct HTTP access");
        }
        BinaryDownload binaryDownload = BinaryDownload.class.cast(binary);
        try {
            String encoding = resource.getResourceMetadata().getCharacterEncoding();
            String fileName = node.getName();
            String mediaType = resource.getResourceMetadata().getContentType();
            BinaryDownloadOptionsBuilder optionsBuilder = BinaryDownloadOptions.builder().withFileName(fileName);
            if (encoding != null) {
                optionsBuilder.withCharacterEncoding(encoding);
            }
            if (mediaType != null) {
                optionsBuilder.withMediaType(mediaType);
            }
            if (isContentDispositionAttachment) {
                optionsBuilder.withDispositionTypeAttachment();
            } else {
                optionsBuilder.withDispositionTypeInline();
            }
            URI uri = binaryDownload.getURI(optionsBuilder.build());
            if (uri == null) {
                throw new IllegalArgumentException("Cannot provide url for downloading the binary property at '" + binaryProperty.getPath() + "'");
            }
            return uri;
        } finally {
            binaryDownload.dispose();
        }
    }

}
