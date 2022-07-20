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

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventListener;
import javax.jcr.observation.ObservationManager;

import org.apache.jackrabbit.api.observation.JackrabbitEventFilter;
import org.apache.jackrabbit.api.observation.JackrabbitObservationManager;
import org.apache.jackrabbit.oak.jcr.observation.filter.FilterFactory;
import org.apache.jackrabbit.oak.jcr.observation.filter.OakEventFilter;
import org.apache.sling.api.resource.observation.ResourceChange.ChangeType;
import org.apache.sling.api.resource.path.Path;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.spi.resource.provider.ObservationReporter;
import org.apache.sling.spi.resource.provider.ObserverConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is the base configuration for a JCR listener, shared
 * by all registered {@link JcrResourceListener}s.
 */
public class JcrListenerBaseConfig implements Closeable {

    private final Logger logger = LoggerFactory.getLogger(JcrListenerBaseConfig.class);

    private final Session session;

    private final ObservationReporter reporter;

    @SuppressWarnings("deprecation")
    public JcrListenerBaseConfig(final @NotNull ObservationReporter reporter, final @NotNull SlingRepository repository) throws RepositoryException {
        this.reporter = reporter;
        // The session should have read access on the whole repository
        this.session = repository.loginService("observation", repository.getDefaultWorkspace());
    }

    /**
     * Dispose this config
     * Close session.
     */
    @Override
    public void close() {
        this.session.logout();
    }

    /**
     * Register a JCR event listener
     * @param listener The listener
     * @param config The configuration
     * @throws RepositoryException If registration fails.
     */
    public void register(final @NotNull EventListener listener, final @NotNull ObserverConfiguration config) throws RepositoryException {
        final ObservationManager mgr = this.session.getWorkspace().getObservationManager();
        if (mgr instanceof JackrabbitObservationManager) {
            final OakEventFilter filter = FilterFactory.wrap(new JackrabbitEventFilter());
            // paths
            setFilterPaths(filter, config);

            // exclude paths
            final Set<String> excludePaths = config.getExcludedPaths().toStringSet();
            if (!excludePaths.isEmpty()) {
                filter.setExcludedPaths(excludePaths.toArray(new String[0]));
            }

            filter.setIsDeep(true);

            // external
            filter.setNoExternal(!config.includeExternal());

            // types
            filter.setEventTypes(getTypes(config));

            // nt:file handling
            filter.withNodeTypeAggregate(new String[]{"nt:file"}, new String[]{"", "jcr:content"});

            // ancestors remove
            filter.withIncludeAncestorsRemove();

            ((JackrabbitObservationManager) mgr).addEventListener(listener, filter);
        } else {
            throw new RepositoryException("Observation manager is not a JackrabbitObservationManager");
        }

    }
    
    private static void setFilterPaths(@NotNull OakEventFilter filter, @NotNull ObserverConfiguration config) {
        final Set<String> paths = config.getPaths().toStringSet();
        // avoid any resizing of these lists
        List<String> pathList = new ArrayList<>(paths.size());
        List<String> globList = new ArrayList<>(paths.size());

        for (final String p : paths) {
            if (p.startsWith(Path.GLOB_PREFIX)) {
                globList.add(p.substring(Path.GLOB_PREFIX.length()));
            } else {
                pathList.add(p);
            }
        }
        if (globList.size() > 0) {
            filter.withIncludeGlobPaths(globList.toArray(new String[0]));
        }
        if (pathList.size() > 0) {
            filter.setAdditionalPaths(pathList.toArray(new String[0]));
        }
    }

    /**
     * Get the event types based on the configuration
     * @param c The configuration
     * @return The event type mask
     */
    private static int getTypes(final @NotNull ObserverConfiguration c) {
        int result = 0;
        for (ChangeType t : c.getChangeTypes()) {
            switch (t) {
                case ADDED:
                    result = result | Event.NODE_ADDED;
                    break;
                case REMOVED:
                    result = result | Event.NODE_REMOVED;
                    break;
                case CHANGED:
                    result = result | Event.PROPERTY_ADDED;
                    result = result | Event.PROPERTY_CHANGED;
                    result = result | Event.PROPERTY_REMOVED;
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    /**
     * Unregister the listener.
     * @param listener The listener
     */
    public void unregister(final EventListener listener) {
        try {
            this.session.getWorkspace().getObservationManager().removeEventListener(listener);
        } catch (final RepositoryException e) {
            logger.warn("Unable to remove session listener: " + this, e);
        }
    }

    /**
     * The observation reporter
     * @return The observation reporter.
     */
    public @NotNull ObservationReporter getReporter() {
        return this.reporter;
    }
}
