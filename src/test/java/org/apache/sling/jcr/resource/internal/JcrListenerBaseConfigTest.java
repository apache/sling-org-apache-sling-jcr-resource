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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.jackrabbit.oak.jcr.observation.filter.OakEventFilter;
import org.apache.sling.api.resource.path.Path;
import org.apache.sling.api.resource.path.PathSet;
import org.apache.sling.spi.resource.provider.ObserverConfiguration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;


public class JcrListenerBaseConfigTest {

    @Test
    public void setPathFilter() {

        OakEventFilter filter = mock(OakEventFilter.class);
        Path globbed = new Path("glob:/globbed/path");
        Path nonglobbed = new Path("/nonglobbed/path");
        ObserverConfiguration config = mock(ObserverConfiguration.class);
        when(config.getPaths()).thenReturn(PathSet.fromPaths(globbed,nonglobbed));

        JcrListenerBaseConfig.setFilterPaths(filter, config);

        ArgumentCaptor<String> pathCaptor = ArgumentCaptor.forClass(String.class);
        verify(filter).setAdditionalPaths(pathCaptor.capture());
        assertEquals("/nonglobbed/path", pathCaptor.getValue());


        ArgumentCaptor<String> globPathCaptor = ArgumentCaptor.forClass(String.class);
        verify(filter).withIncludeGlobPaths(globPathCaptor.capture());
        assertEquals("/globbed/path", globPathCaptor.getValue());
    }
}
