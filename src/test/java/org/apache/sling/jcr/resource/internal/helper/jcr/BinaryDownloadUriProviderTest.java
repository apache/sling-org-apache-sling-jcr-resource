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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.ValueFormatException;

import org.apache.jackrabbit.api.binary.BinaryDownload;
import org.apache.jackrabbit.api.binary.BinaryDownloadOptions;
import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.external.URIProvider.Operation;
import org.apache.sling.api.resource.external.URIProvider.Scope;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BinaryDownloadUriProviderTest {

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.JCR_OAK);

    private Session session;
    private BinaryDownloadUriProvider uriProvider;
    private Resource fileResource;

    @Mock
    private BinaryDownload binaryDownload;

    @Mock
    private Property property;

    @Before
    public void setUp() throws IOException, RepositoryException {
        uriProvider = new BinaryDownloadUriProvider(false);
        session = context.resourceResolver().adaptTo(Session.class);
        try (InputStream input = this.getClass().getResourceAsStream("/SLING-INF/nodetypes/folder.cnd")) {
            JcrUtils.putFile(session.getRootNode(), "test", "myMimeType", input);
        }
        fileResource = context.resourceResolver().getResource("/test");
    }

    @Test
    public void testMockedProperty() throws ValueFormatException, RepositoryException, URISyntaxException {
        uriProvider = new BinaryDownloadUriProvider(false) {
            @Override
            protected Property getPrimaryProperty(Node node) throws RepositoryException {
                return property;
            }
        };
        Mockito.when(property.getBinary()).thenReturn(binaryDownload);
        URI myUri = new URI("https://example.com/mybinary");
        Mockito.when(binaryDownload.getURI(Matchers.any(BinaryDownloadOptions.class))).thenReturn(myUri);

        assertEquals(myUri, uriProvider.toURI(fileResource, Scope.EXTERNAL, Operation.READ));
        ArgumentCaptor<BinaryDownloadOptions> argumentCaptor = ArgumentCaptor.forClass(BinaryDownloadOptions.class);
        Mockito.verify(binaryDownload).getURI(argumentCaptor.capture());
        assertEquals("myMimeType", argumentCaptor.getValue().getMediaType());
        assertEquals("test", argumentCaptor.getValue().getFileName());
        assertNull(argumentCaptor.getValue().getCharacterEncoding());
    }

    @Test
    public void testPropertyWithoutExternallyAccessibleBlobStore() throws URISyntaxException, RepositoryException, IOException {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> uriProvider.toURI(fileResource, Scope.EXTERNAL, Operation.READ));
        assertEquals("Cannot provide url for downloading the binary property at '/test/jcr:content/jcr:data'", e.getMessage());
    }

    @Test
    public void testNoPrimaryPropertyUri() {
        Resource resource = context.create().resource("/content/test1", Collections.singletonMap("jcr:primaryProperty", "nt:folder"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> uriProvider.toURI(resource, Scope.PUBLIC, Operation.READ));
        assertEquals("Node does not have a primary property", e.getMessage());
    }

    @Test
    public void testUnsupportedScope() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> uriProvider.toURI(fileResource, Scope.INTERNAL, Operation.READ));
        assertEquals("This provider only provides URIs for 'READ' operations in scope 'PUBLIC' or 'EXTERNAL', but not for scope 'INTERNAL' and operation 'READ'", e.getMessage());
    }

    @Test
    public void testUnsupportedOperation() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> uriProvider.toURI(fileResource, Scope.EXTERNAL, Operation.UPDATE));
        assertEquals("This provider only provides URIs for 'READ' operations in scope 'PUBLIC' or 'EXTERNAL', but not for scope 'EXTERNAL' and operation 'UPDATE'", e.getMessage());
    }
}
