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

import java.io.Closeable;
import java.io.IOException;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.api.JackrabbitSession;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.sling.api.SlingException;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceUtil;
import org.apache.sling.api.resource.external.URIProvider;
import org.apache.sling.commons.classloader.DynamicClassLoaderManager;
import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.apache.sling.jcr.resource.internal.JcrListenerBaseConfig;
import org.apache.sling.jcr.resource.internal.JcrModifiableValueMap;
import org.apache.sling.jcr.resource.internal.JcrResourceListener;
import org.apache.sling.spi.resource.provider.ObserverConfiguration;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.apache.sling.spi.resource.provider.QueryLanguageProvider;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.apache.sling.spi.resource.provider.ResourceContext;
import org.apache.sling.spi.resource.provider.ResourceProvider;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.sling.jcr.resource.internal.helper.jcr.ContextUtil.getHelperData;
import static org.apache.sling.jcr.resource.internal.helper.jcr.ContextUtil.getResourceFactory;
import static org.apache.sling.jcr.resource.internal.helper.jcr.ContextUtil.getSession;

@Component(name="org.apache.sling.jcr.resource.internal.helper.jcr.JcrResourceProviderFactory",
           service = ResourceProvider.class,
           property = {
                   ResourceProvider.PROPERTY_NAME + "=JCR",
                   ResourceProvider.PROPERTY_ROOT + "=/",
                   ResourceProvider.PROPERTY_MODIFIABLE + ":Boolean=true",
                   ResourceProvider.PROPERTY_ADAPTABLE + ":Boolean=true",
                   ResourceProvider.PROPERTY_ATTRIBUTABLE + ":Boolean=true",
                   ResourceProvider.PROPERTY_REFRESHABLE + ":Boolean=true",
                   ResourceProvider.PROPERTY_AUTHENTICATE + "=" + ResourceProvider.AUTHENTICATE_REQUIRED,
                   Constants.SERVICE_VENDOR + "=The Apache Software Foundation"
           })
public class JcrResourceProvider extends ResourceProvider<JcrProviderState> {

    /** Logger */
    private final Logger logger = LoggerFactory.getLogger(JcrResourceProvider.class);

    private static final String REPOSITORY_REFERENCE_NAME = "repository";

    private static final Set<String> IGNORED_PROPERTIES = new HashSet<>();
    static {
        IGNORED_PROPERTIES.add(JcrConstants.JCR_MIXINTYPES);
        IGNORED_PROPERTIES.add(JcrConstants.JCR_PRIMARYTYPE);
        IGNORED_PROPERTIES.add(JcrConstants.JCR_CREATED);
        IGNORED_PROPERTIES.add("jcr:createdBy");
    }

    @Reference(name = REPOSITORY_REFERENCE_NAME, service = SlingRepository.class)
    private ServiceReference<SlingRepository> repositoryReference;

    /** The JCR listener base configuration. */
    private volatile JcrListenerBaseConfig listenerConfig;

    /** The JCR observation listeners. */
    private final Map<ObserverConfiguration, Closeable> listeners = new HashMap<>();

    /**
     * Map of bound URIProviders sorted by service ranking in descending order (highest ranking first).
     * Key = service reference, value = service implementation
     */
    private final SortedMap<ServiceReference<URIProvider>, URIProvider> providers = Collections.synchronizedSortedMap(new TreeMap<>(Collections.reverseOrder()));

    private volatile SlingRepository repository;

    private volatile JcrProviderStateFactory stateFactory;

    private final AtomicReference<DynamicClassLoaderManager> classLoaderManagerReference = new AtomicReference<>();

    private AtomicReference<URIProvider[]> uriProviderReference = new AtomicReference<>();

    @Activate
    protected void activate(final ComponentContext context) {
        SlingRepository repository = context.locateService(REPOSITORY_REFERENCE_NAME,
                this.repositoryReference);
        if (repository == null) {
            // concurrent unregistration of SlingRepository service
            // don't care, this component is going to be deactivated
            // so we just stop working
            logger.warn("activate: Activation failed because SlingRepository may have been unregistered concurrently");
            return;
        }

        this.repository = repository;

        this.stateFactory = new JcrProviderStateFactory(repositoryReference, repository,
                classLoaderManagerReference, uriProviderReference);
    }

    @Deactivate
    protected void deactivate() {
        this.stateFactory = null;
    }

    @SuppressWarnings("unused")
    @Reference(name = "dynamicClassLoaderManager",
            service = DynamicClassLoaderManager.class,
            cardinality = ReferenceCardinality.OPTIONAL, policy = ReferencePolicy.DYNAMIC)
    protected void bindDynamicClassLoaderManager(final DynamicClassLoaderManager dynamicClassLoaderManager) {
        this.classLoaderManagerReference.set(dynamicClassLoaderManager);
    }

    @SuppressWarnings("unused")
    protected void unbindDynamicClassLoaderManager(final DynamicClassLoaderManager dynamicClassLoaderManager) {
        this.classLoaderManagerReference.compareAndSet(dynamicClassLoaderManager, null);
    }

    @Reference(
            name = "uriprovider",
            service = URIProvider.class,
            cardinality = ReferenceCardinality.MULTIPLE,
            policy = ReferencePolicy.DYNAMIC,
            bind = "bindUriProvider",
            unbind = "unbindUriProvider"
    )

    @SuppressWarnings("unused")
    private void bindUriProvider(ServiceReference<URIProvider> srUriProvider, URIProvider uriProvider) {
        providers.put(srUriProvider, uriProvider);
        updateURIProviders();
    }

    @SuppressWarnings("unused")
    private void unbindUriProvider(ServiceReference<URIProvider> srUriProvider) {
        providers.remove(srUriProvider);
        updateURIProviders();
    }

    private void updateURIProviders() {
        URIProvider[] ups = providers.values().toArray(new URIProvider[providers.size()]);
        this.uriProviderReference.set(ups);
    }

    @Override
    public void start(final ProviderContext ctx) {
        super.start(ctx);
        this.registerListeners();
    }

    @Override
    public void stop() {
        this.unregisterListeners();
        super.stop();
    }

    @Override
    public void update(final long changeSet) {
        super.update(changeSet);
        this.updateListeners();
    }

    @SuppressWarnings("unused")
    private void bindRepository(final ServiceReference<SlingRepository> ref) {
        this.repositoryReference = ref;
        this.repository = null;
    }

    @SuppressWarnings("unused")
    private void unbindRepository(final ServiceReference<SlingRepository> ref) {
        if (this.repositoryReference == ref) {
            this.repositoryReference = null;
            this.repository = null;
        }
    }

    /**
     * Register all observation listeners.
     */
    private void registerListeners() {
        if ( this.repository != null ) {
            logger.debug("Registering resource listeners...");
            try {
                this.listenerConfig = new JcrListenerBaseConfig(this.getProviderContext().getObservationReporter(),
                    this.repository);
                for(final ObserverConfiguration config : this.getProviderContext().getObservationReporter().getObserverConfigurations()) {
                    logger.debug("Registering listener for {}", config.getPaths());
                    final Closeable listener = new JcrResourceListener(this.listenerConfig, config);
                    this.listeners.put(config, listener);
                }
            } catch (final RepositoryException e) {
                throw new SlingException("Can't create the JCR event listener.", e);
            }
            logger.debug("Registered resource listeners");
        }
    }

    /**
     * Unregister all observation listeners.
     */
    private void unregisterListeners() {
        logger.debug("Unregistering resource listeners...");
        for(final Closeable c : this.listeners.values()) {
            try {
                logger.debug("Removing listener for {}", ((JcrResourceListener)c).getConfig().getPaths());
                c.close();
            } catch (final IOException e) {
                // ignore this as the method above does not throw it
            }
        }
        this.listeners.clear();
        if ( this.listenerConfig != null ) {
            this.listenerConfig.close();
            this.listenerConfig = null;
        }
        logger.debug("Unregistered resource listeners");
    }

    /**
     * Update observation listeners.
     */
    private void updateListeners() {
        if ( this.listenerConfig == null ) {
            this.unregisterListeners();
            this.registerListeners();
        } else {
            logger.debug("Updating resource listeners...");
            final Map<ObserverConfiguration, Closeable> oldMap = new HashMap<>(this.listeners);
            this.listeners.clear();
            try {
                for(final ObserverConfiguration config : this.getProviderContext().getObservationReporter().getObserverConfigurations()) {
                    // check if such a listener already exists
                    Closeable listener = oldMap.remove(config);
                    if ( listener == null ) {
                        logger.debug("Registering listener for {}", config.getPaths());
                        listener = new JcrResourceListener(this.listenerConfig, config);
                    } else {
                        logger.debug("Updating listener for {}", config.getPaths());
                        ((JcrResourceListener)listener).update(config);
                    }
                    this.listeners.put(config, listener);
                }
            } catch (final RepositoryException e) {
                throw new SlingException("Can't create the JCR event listener.", e);
            }
            for(final Closeable c : oldMap.values()) {
                try {
                    logger.debug("Removing listener for {}", ((JcrResourceListener)c).getConfig().getPaths());
                    c.close();
                } catch (final IOException e) {
                    // ignore this as the method above does not throw it
                }
            }
            logger.debug("Updated resource listeners");
        }
    }

    /**
     * Create a new ResourceResolver wrapping a Session object. Carries map of
     * authentication info in order to create a new resolver as needed.
     */
    @Override
    public @NotNull JcrProviderState authenticate(final @NotNull Map<String, Object> authenticationInfo)
    throws LoginException {
        return stateFactory.createProviderState(authenticationInfo);
    }

    @Override
    public void logout(final @Nullable JcrProviderState state) {
        if (state != null) {
            state.logout();
        }
    }

    @Override
    public boolean isLive(final @NotNull ResolveContext<JcrProviderState> ctx) {
        return getSession(ctx).isLive();
    }

    @Override
    public @Nullable Resource getResource(@NotNull ResolveContext<JcrProviderState> ctx, @NotNull String path, @NotNull ResourceContext rCtx, @Nullable Resource parent) {
        try {
            return getResourceFactory(ctx).createResource(ctx.getResourceResolver(), path, parent, rCtx.getResolveParameters());
        } catch (RepositoryException e) {
            throw new SlingException("Can't get resource", e);
        }
    }

    @Override
    public @Nullable Iterator<Resource> listChildren(@NotNull ResolveContext<JcrProviderState> ctx, @NotNull Resource parent) {
        JcrItemResource<?> parentItemResource;

        // short cut for known JCR resources
        if (parent instanceof JcrItemResource) {
            parentItemResource = (JcrItemResource<?>) parent;
        } else {
            // try to get the JcrItemResource for the parent path to list
            // children
            try {
                parentItemResource = getResourceFactory(ctx).createResource(
                        parent.getResourceResolver(), parent.getPath(), null,
                        parent.getResourceMetadata().getParameterMap());
            } catch (RepositoryException re) {
                throw new SlingException("Can't list children", re);
            }
        }

        // return children if there is a parent item resource, else null
        return (parentItemResource != null)
                ? parentItemResource.listJcrChildren()
                : null;
    }

    @Override
    public @Nullable Resource getParent(final @NotNull ResolveContext<JcrProviderState> ctx, final @NotNull Resource child) {
        if (child instanceof JcrItemResource<?>) {
            String version = null;
            if (child.getResourceMetadata().getParameterMap() != null) {
                version = child.getResourceMetadata().getParameterMap().get("v");
            }
            if (version == null) {
                String parentPath = ResourceUtil.getParent(child.getPath());
                if (parentPath != null) {
                    Item childItem = ((JcrItemResource) child).getItem();
                    Node parentNode = getResourceFactory(ctx).getParentOrNull(childItem, parentPath);
                    if (parentNode != null) {
                        return new JcrNodeResource(ctx.getResourceResolver(), parentPath, null, parentNode, getHelperData(ctx));
                    }
                }
            }
            return null;
        }
        return super.getParent(ctx, child);
    }

    @Override
    public @NotNull Collection<String> getAttributeNames(final @NotNull ResolveContext<JcrProviderState> ctx) {
        final Set<String> names = new HashSet<>();
        final String[] sessionNames = getSession(ctx).getAttributeNames();
        for(final String name : sessionNames) {
            if ( isAttributeVisible(name) ) {
                names.add(name);
            }
        }
        return names;
    }

    @Override
    public @Nullable Object getAttribute(final @NotNull ResolveContext<JcrProviderState> ctx, final @NotNull String name) {
        if (isAttributeVisible(name)) {
            if (ResourceResolverFactory.USER.equals(name)) {
                return getSession(ctx).getUserID();
            }
            return getSession(ctx).getAttribute(name);
        }
        return null;
    }

    @Override
    public Resource create(final @NotNull ResolveContext<JcrProviderState> ctx, final String path, final Map<String, Object> properties) throws PersistenceException {
        // check for node type
        final Object nodeObj = (properties != null ? properties.get(JcrConstants.JCR_PRIMARYTYPE) : null);
        // check for sling:resourcetype
        final String nodeType;
        if (nodeObj != null) {
            nodeType = nodeObj.toString();
        } else {
            final Object rtObj = (properties != null ? properties.get(JcrResourceConstants.SLING_RESOURCE_TYPE_PROPERTY) : null);
            boolean isNodeType = false;
            if (rtObj != null) {
                final String resourceType = rtObj.toString();
                if (resourceType.indexOf(':') != -1 && resourceType.indexOf('/') == -1) {
                    try {
                        getSession(ctx).getWorkspace().getNodeTypeManager().getNodeType(resourceType);
                        isNodeType = true;
                    } catch (final RepositoryException ignore) {
                        // we expect this, if this isn't a valid node type, therefore ignoring
                    }
                }
            }
            if (isNodeType) {
                nodeType = rtObj.toString();
            } else {
                nodeType = null;
            }
        }
        final String jcrPath = path;
        if (jcrPath == null) {
            throw new PersistenceException("Unable to create node at " + path, null, path, null);
        }
        Node node = null;
        try {
            final int lastPos = jcrPath.lastIndexOf('/');
            final Node parent;
            if (lastPos == 0) {
                parent = getSession(ctx).getRootNode();
            } else {
                parent = (Node) getSession(ctx).getItem(jcrPath.substring(0, lastPos));
            }
            final String name = jcrPath.substring(lastPos + 1);
            if (nodeType != null) {
                node = parent.addNode(name, nodeType);
            } else {
                node = parent.addNode(name);
            }

            if (properties != null) {
                // create modifiable map
                final JcrModifiableValueMap jcrMap = new JcrModifiableValueMap(node, getHelperData(ctx));
                // check mixin types first
                final Object value = properties.get(JcrConstants.JCR_MIXINTYPES);
                if (value != null) {
                    jcrMap.put(JcrConstants.JCR_MIXINTYPES, value);
                }
                for (final Map.Entry<String, Object> entry : properties.entrySet()) {
                    if (!IGNORED_PROPERTIES.contains(entry.getKey())) {
                        try {
                            jcrMap.put(entry.getKey(), entry.getValue());
                        } catch (final IllegalArgumentException iae) {
                            try {
                                node.remove();
                            } catch (final RepositoryException re) {
                                // we ignore this
                            }
                            throw new PersistenceException(iae.getMessage(), iae, path, entry.getKey());
                        }
                    }
                }
            }

            return new JcrNodeResource(ctx.getResourceResolver(), path, null, node, getHelperData(ctx));
        } catch (final RepositoryException e) {
            throw new PersistenceException("Unable to create node at " + jcrPath, e, path, null);
        }
    }

    @Override
    public boolean orderBefore(@NotNull ResolveContext<JcrProviderState> ctx, @NotNull Resource parent, @NotNull String name,
            @Nullable String followingSiblingName) throws PersistenceException {
        Node node = parent.adaptTo(Node.class);
        if (node == null) {
            throw new PersistenceException("The resource " + parent.getPath() + " cannot be adapted to Node. It is probably not provided by the JcrResourceProvider");
        }
        try {
            // check if reordering necessary
            NodeIterator nodeIterator = node.getNodes();
            long existingNodePosition = -1;
            long index = 0;
            while (nodeIterator.hasNext()) {
                Node childNode = nodeIterator.nextNode();
                if (childNode.getName().equals(name)) {
                    existingNodePosition = index;
                }
                if (existingNodePosition >= 0) {
                    // is existing resource already at the desired position?
                    if (childNode.getName().equals(followingSiblingName)) {
                        if (existingNodePosition == index-1) {
                            return false;
                        }
                    }
                    // is the existing node already the last one in the list?
                    else if (followingSiblingName == null && existingNodePosition == nodeIterator.getSize()-1) {
                        return false;
                    }
                }
                index++;
            }
            node.orderBefore(name, followingSiblingName);
            return true;
        } catch (final RepositoryException e) {
            throw new PersistenceException("Unable to reorder children below " + parent.getPath(), e, parent.getPath(), null);
        }
    }

    @Override
    public void delete(final @NotNull ResolveContext<JcrProviderState> ctx, final @NotNull Resource resource)
    throws PersistenceException {
        // try to adapt to Item
        Item item = resource.adaptTo(Item.class);
        try {
            if ( item == null ) {
                final String jcrPath = resource.getPath();
                if (jcrPath == null) {
                    logger.debug("delete: {} maps to an empty JCR path", resource.getPath());
                    throw new PersistenceException("Unable to delete resource", null, resource.getPath(), null);
                }
                item = getSession(ctx).getItem(jcrPath);
            }
            item.remove();
        } catch (final RepositoryException e) {
            throw new PersistenceException("Unable to delete resource", e, resource.getPath(), null);
        }
    }

    @Override
    public void revert(final @NotNull ResolveContext<JcrProviderState> ctx) {
        try {
            getSession(ctx).refresh(false);
        } catch (final RepositoryException ignore) {
            logger.warn("Unable to revert pending changes.", ignore);
        }
    }

    @Override
    public void commit(final @NotNull ResolveContext<JcrProviderState> ctx)
    throws PersistenceException {
        try {
            getSession(ctx).save();
        } catch (final RepositoryException e) {
            throw new PersistenceException("Unable to commit changes to session.", e);
        }
    }

    @Override
    public boolean hasChanges(final @NotNull ResolveContext<JcrProviderState> ctx) {
        try {
            return getSession(ctx).hasPendingChanges();
        } catch (final RepositoryException ignore) {
            logger.warn("Unable to check session for pending changes.", ignore);
        }
        return false;
    }

    @Override
    public void refresh(final @NotNull ResolveContext<JcrProviderState> ctx) {
        try {
            getSession(ctx).refresh(true);
        } catch (final RepositoryException ignore) {
            logger.warn("Unable to refresh session.", ignore);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public @Nullable <AdapterType> AdapterType adaptTo(final @NotNull ResolveContext<JcrProviderState> ctx,
            final @NotNull Class<AdapterType> type) {
        Session session = getSession(ctx);
        if (type == Session.class) {
            return (AdapterType) session;
        } else if (type == Principal.class) {
            try {
                if (session instanceof JackrabbitSession && session.getUserID() != null) {
                    JackrabbitSession s =((JackrabbitSession) session);
                    final UserManager um = s.getUserManager();
                    if (um != null) {
                        final Authorizable auth = um.getAuthorizable(s.getUserID());
                        if (auth != null) {
                            return (AdapterType) auth.getPrincipal();
                        }
                    }
                }
                logger.debug("not able to adapto Resource to Principal, let the base class try to adapt");
            } catch (RepositoryException e) {
                logger.warn("error while adapting Resource to Principal, let the base class try to adapt", e);
            }
        }
        return super.adaptTo(ctx, type);
    }

    @Override
    public boolean copy(final @NotNull ResolveContext<JcrProviderState> ctx,
                        final @NotNull String srcAbsPath,
                        final @NotNull String destAbsPath) {
        return false;
    }

    @Override
    public boolean move(final @NotNull ResolveContext<JcrProviderState> ctx,
                        final @NotNull String srcAbsPath,
                        final @NotNull String destAbsPath) throws PersistenceException {
        final String dstNodePath = destAbsPath + '/' + ResourceUtil.getName(srcAbsPath);
        try {
            getSession(ctx).move(srcAbsPath, dstNodePath);
            return true;
        } catch (final RepositoryException e) {
            throw new PersistenceException("Unable to move resource to " + destAbsPath, e, srcAbsPath, null);
        }
    }

    @Override
    public @Nullable QueryLanguageProvider<JcrProviderState> getQueryLanguageProvider() {
        final ProviderContext ctx = this.getProviderContext();
        if (ctx != null) {
            return new BasicQueryLanguageProvider(ctx);
        }
        return null;
    }

    /**
     * Returns <code>true</code> unless the name is
     * <code>user.jcr.credentials</code> (
     * {@link JcrResourceConstants#AUTHENTICATION_INFO_CREDENTIALS}) or contains
     * the string <code>password</code> as in <code>user.password</code> (
     * {@link org.apache.sling.api.resource.ResourceResolverFactory#PASSWORD})
     *
     * @param name The name to check whether it is visible or not
     * @return <code>true</code> if the name is assumed visible
     * @throws NullPointerException if <code>name</code> is <code>null</code>
     */
    private static boolean isAttributeVisible(final String name) {
        return !name.equals(JcrResourceConstants.AUTHENTICATION_INFO_CREDENTIALS)
            && !name.contains("password");
    }
}
