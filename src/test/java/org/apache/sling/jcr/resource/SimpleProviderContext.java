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
package org.apache.sling.jcr.resource;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.path.PathSet;
import org.apache.sling.spi.resource.provider.ObservationReporter;
import org.apache.sling.spi.resource.provider.ObserverConfiguration;
import org.apache.sling.spi.resource.provider.ProviderContext;

public class SimpleProviderContext implements ProviderContext {
    @Override
    public ObservationReporter getObservationReporter() {
        return new ObservationReporter() {

            @Override
            public void reportChanges(Iterable<ResourceChange> changes, boolean distribute) {
            }

            @Override
            public void reportChanges(ObserverConfiguration config, Iterable<ResourceChange> changes,
                    boolean distribute) {
            }

            @Override
            public List<ObserverConfiguration> getObserverConfigurations() {
                ObserverConfiguration config = new ObserverConfiguration() {

                    @Override
                    public boolean includeExternal() {
                        return true;
                    }

                    @Override
                    public PathSet getPaths() {
                        return PathSet.fromStrings("/");
                    }

                    @Override
                    public PathSet getExcludedPaths() {
                        return PathSet.fromPaths();
                    }

                    @Override
                    public Set<ChangeType> getChangeTypes() {
                        return EnumSet.allOf(ChangeType.class);
                    }

                    @Override
                    public boolean matches(String path) {
                        return true;
                    }

                    @Override
                    public Set<String> getPropertyNamesHint() {
                        return new HashSet<String>();
                    }
                };
                return Collections.singletonList(config);
            }
        };
    }

    @Override
    public PathSet getExcludedPaths() {
        return PathSet.fromPaths();
    }
}