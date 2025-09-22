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

import java.io.IOException;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.util.ISO9075;
import org.apache.jackrabbit.util.Text;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.internal.helper.JcrPropertyMapCacheEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This implementation of the value map allows to change
 * the properties.
 */
public class JcrValueMap implements ValueMap {

    /** The underlying node. */
    protected final Node node;

    /** A cache for the properties. */
    protected final Map<String, JcrPropertyMapCacheEntry> cache = new LinkedHashMap<>();

    /** A cache for the values. */
    protected final Map<String, Object> valueCache = new LinkedHashMap<>();

    /** Has the node been read completely? */
    private boolean fullyRead = false;

    /** Helper data object */
    protected final HelperData helper;

    /**
     * Constructor
     * @param node The underlying node.
     * @param helper Helper data object
     */
    public JcrValueMap(final @NotNull Node node, final @NotNull HelperData helper) {
        this.node = node;
        this.helper = helper;
    }

    // ---------- ValueMap

    protected String checkKey(final String key) {
        if (key == null) {
            throw new NullPointerException("Key must not be null.");
        }
        if (key.startsWith("./")) {
            return key.substring(2);
        }
        return key;
    }

    /**
     * @see org.apache.sling.api.resource.ValueMap#get(java.lang.String, java.lang.Class)
     * 
     * Note: The {@code type} parameter is marked as @NonNull in the API documentation, but
     * https://issues.apache.org/jira/browse/SLING-11567 it got obvious that this assumption
     * does not hold true (this change actually broke b/w compatibility).
     * That means we still have to handle the case that {@code type} is null.
     * 
     * This is also recommended by the API documentation of this method.
     * 
     */
    @Override
    @SuppressWarnings({"unchecked","java:S2583"})
    public <T> T get(final @NotNull String aKey, @NotNull final Class<T> type) {
        final String key = checkKey(aKey);
        if (type == null) {
            return (T) get(key);
        }
        final JcrPropertyMapCacheEntry entry = this.read(key);
        if (entry == null) {
            return null;
        }
        return entry.convertToType(type, node, helper.getDynamicClassLoader());
    }

    /**
     * @see org.apache.sling.api.resource.ValueMap#get(java.lang.String, java.lang.Object)
     * 
     * Note: The {@code defaultValue} parameter is marked as @NonNull in the API documentation, but
     * https://issues.apache.org/jira/browse/SLING-11567 it got obvious that this assumption
     * does not hold true (this change actually broke b/w compatibility).
     * That means we still have to handle the case that {@code defaultValue} is null.
     * 
     * This is also recommended by the API documentation of this method.
     * 
     */
    @Override
    @SuppressWarnings({"unchecked","java:S2583"})
    public <T> @NotNull T get(final @NotNull String aKey, @NotNull final T defaultValue) {
        final String key = checkKey(aKey);
        if (defaultValue == null) {
            return (T) get(key);
        }

        // special handling in case the default value implements one
        // of the interface types supported by the convertToType method
        Class<T> type = (Class<T>) normalizeClass(defaultValue.getClass());

        T value = get(key, type);
        if (value == null) {
            value = defaultValue;
        }

        return value;
    }

    // ---------- Map

    /**
     * @see java.util.Map#get(java.lang.Object)
     */
    @Override
    public Object get(final Object aKey) {
        final String key = checkKey(aKey.toString());
        final JcrPropertyMapCacheEntry entry = this.read(key);
        return (entry == null ? null : entry.getPropertyValueOrNull());
    }

    /**
     * @see java.util.Map#containsKey(java.lang.Object)
     */
    @Override
    public boolean containsKey(final Object key) {
        return get(key) != null;
    }

    /**
     * @see java.util.Map#containsValue(java.lang.Object)
     */
    @Override
    public boolean containsValue(final Object value) {
        readFully();
        return valueCache.containsValue(value);
    }

    /**
     * @see java.util.Map#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    /**
     * @see java.util.Map#size()
     */
    @Override
    public int size() {
        readFully();
        return cache.size();
    }

    /**
     * @see java.util.Map#entrySet()
     */
    @Override
    public @NotNull Set<java.util.Map.Entry<String, Object>> entrySet() {
        readFully();
        final Map<String, Object> sourceMap;
        if (cache.size() == valueCache.size()) {
            sourceMap = valueCache;
        } else {
            sourceMap = transformEntries(cache);
        }
        return Collections.unmodifiableSet(sourceMap.entrySet());
    }

    /**
     * @see java.util.Map#keySet()
     */
    @Override
    public @NotNull Set<String> keySet() {
        readFully();
        return Collections.unmodifiableSet(cache.keySet());
    }

    /**
     * @see java.util.Map#values()
     */
    @Override
    public @NotNull Collection<Object> values() {
        readFully();
        final Map<String, Object> sourceMap;
        if (cache.size() == valueCache.size()) {
            sourceMap = valueCache;
        } else {
            sourceMap = transformEntries(cache);
        }
        return Collections.unmodifiableCollection(sourceMap.values());
    }

    /**
     * Return the path of the current node.
     *
     * @return the path
     * @throws IllegalStateException If a repository exception occurs
     */
    public String getPath() {
        try {
            return node.getPath();
        } catch (final RepositoryException e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- Helpers to access the node's property ------------------------

    /**
     * Put a single property into the cache
     * @param prop The property to be cached
     * @return A JcrPropertyMapCacheEntry for the given property
     * @throws IllegalArgumentException if a repository exception occurs
     */
    private @NotNull JcrPropertyMapCacheEntry cacheProperty(final @NotNull Property prop) {
        try {
            // calculate the key
            final String name = prop.getName();
            String key = null;
            if (name.contains("_x")) {
                // for compatibility with older versions we use the (wrong)
                // ISO9075 path encoding
                key = ISO9075.decode(name);
                if (key.equals(name)) {
                    key = null;
                }
            }
            if (key == null) {
                key = Text.unescapeIllegalJcrChars(name);
            }
            JcrPropertyMapCacheEntry entry = cache.get(key);
            if (entry == null) {
                entry = new JcrPropertyMapCacheEntry(prop);
                cache.put(key, entry);
                valueCache.put(key, entry.getPropertyValue());
            }
            return entry;
        } catch (final RepositoryException re) {
            throw new IllegalArgumentException(re);
        }
    }

    private @NotNull JcrPropertyMapCacheEntry cacheProperty(final @NotNull String key, final @NotNull Object value, final @NotNull Node node) {
        try {
            JcrPropertyMapCacheEntry entry = cache.get(key);
            if (entry == null) {
                entry = new JcrPropertyMapCacheEntry(value, node);
                cache.put(key, entry);
                valueCache.put(key, entry.getPropertyValue());
            }
            return entry;
        } catch (final RepositoryException|IOException re) {
            throw new IllegalArgumentException(re);
        }
    }

    /**
     * Reads the primary type of the current node via {@link Node#getPrimaryNodeType()} and caches it.
     * That way regular permission evaluation is bypassed (see <a href="https://issues.apache.org/jira/browse/OAK-2441">OAK-2441</a>).
     * Should only be used as fallback if regular access via {@link Node#getProperty(String)} fails as 
     * calculating the NodeType is expensive.
     * @return the cache entry for the primary type
     */
    private JcrPropertyMapCacheEntry readPrimaryType() {
        try {
            String primaryType = node.getPrimaryNodeType().getName();
            return cacheProperty(JcrConstants.JCR_PRIMARYTYPE, primaryType, node);
        } catch (final RepositoryException re) {
            throw new IllegalArgumentException(re);
        }
    }
    /**
     * Read a single property.
     * @throws IllegalArgumentException if a repository exception occurs
     */
    @Nullable JcrPropertyMapCacheEntry read(final @NotNull String name) {
        // check for empty key
        if (name.length() == 0) {
            return null;
        }
        // if the name is a path, we should handle this differently
        if (name.indexOf('/') != -1) {
            return readPath(name);
        }

        // check cache
        JcrPropertyMapCacheEntry cachedValued = cache.get(name);
        if (fullyRead || cachedValued != null) {
            return cachedValued;
        }

        try {
            final String key = escapeKeyName(name);
            Property property = NodeUtil.getPropertyOrNull(node,key);
            if (property == null) { 
                if (name.equals(ResourceResolver.PROPERTY_RESOURCE_TYPE)) {
                    // special handling for the resource type property which according to the API must always be exposed via property sling:resourceType
                    // use value of jcr:primaryType if sling:resourceType is not set
                    JcrPropertyMapCacheEntry entry = read(JcrConstants.JCR_PRIMARYTYPE);
                    if (entry != null) {
                        return cacheProperty(name, entry.getPropertyValue(), node);
                    }
                } else if (name.equals(JcrConstants.JCR_PRIMARYTYPE)) {
                    return readPrimaryType();
                }
            }
            if (property != null) {
                return cacheProperty(property);
            }
        } catch (final RepositoryException re) {
            throw new IllegalArgumentException(re);
        }
        return null;
    }

    private @Nullable JcrPropertyMapCacheEntry readPath(@NotNull String name) {
        // first a compatibility check with the old (wrong) ISO9075 encoding
        final String path = ISO9075.encodePath(name);
        try {
            Property property = NodeUtil.getPropertyOrNull(node, path);
            if (property != null) {
                return new JcrPropertyMapCacheEntry(property);
            }
        } catch (final RepositoryException re) {
            throw new IllegalArgumentException(re);
        }
        // now we do a proper segment by segment encoding
        final StringBuilder sb = new StringBuilder();
        int pos = 0;
        int lastPos = -1;
        while (pos < name.length()) {
            if (name.charAt(pos) == '/') {
                if (lastPos + 1 < pos) {
                    sb.append(Text.escapeIllegalJcrChars(name.substring(lastPos + 1, pos)));
                }
                sb.append('/');
                lastPos = pos;
            }
            pos++;
        }
        if (lastPos + 1 < pos) {
            sb.append(Text.escapeIllegalJcrChars(name.substring(lastPos + 1)));
        }
        final String newPath = sb.toString();
        try {
            Property property = NodeUtil.getPropertyOrNull(node,newPath);
            if (property != null) {
                return new JcrPropertyMapCacheEntry(property);
            }
        } catch (final RepositoryException re) {
            throw new IllegalArgumentException(re);
        }

        return null;
    }

    /**
     * Handles key name escaping by taking into consideration if it contains a
     * registered prefix
     *
     * @param key the key to escape
     * @return escaped key name
     * @throws RepositoryException if the repository's namespace prefixes cannot be retrieved
     */
    protected @NotNull String escapeKeyName(final @NotNull String key) throws RepositoryException {
        final int indexOfPrefix = key.indexOf(':');
        // check if colon is neither the first nor the last character
        if (indexOfPrefix > 0 && key.length() > indexOfPrefix + 1) {
            final String prefix = key.substring(0, indexOfPrefix);
            for (final String existingPrefix : this.helper.getNamespacePrefixes(this.node.getSession())) {
                if (existingPrefix.equals(prefix)) {
                    return prefix
                            + ":"
                            + Text.escapeIllegalJcrChars(key
                            .substring(indexOfPrefix + 1));
                }
            }
        }
        return Text.escapeIllegalJcrChars(key);
    }

    /**
     * Read all properties.
     * @throws IllegalArgumentException if a repository exception occurs
     */
    void readFully() {
        if (!fullyRead) {
            try {
                final PropertyIterator pi = node.getProperties();
                while (pi.hasNext()) {
                    final Property prop = pi.nextProperty();
                    this.cacheProperty(prop);
                }
                // make sure primary type is in cache
                if (!this.cache.containsKey(JcrConstants.JCR_PRIMARYTYPE)) {
                    readPrimaryType();
                }
                // make sure sling:resourceType is in cache
                if (!this.cache.containsKey(ResourceResolver.PROPERTY_RESOURCE_TYPE)) {
                    // special handling for the resource type property which according to the API must always be exposed via property sling:resourceType
                    // use value of jcr:primaryType if sling:resourceType is not set
                    this.cacheProperty(ResourceResolver.PROPERTY_RESOURCE_TYPE, valueCache.get(JcrConstants.JCR_PRIMARYTYPE), node);
                }
                fullyRead = true;
            } catch (final RepositoryException re) {
                throw new IllegalArgumentException(re);
            }
        }
    }

    // ---------- Implementation helper

    private static Class<?> normalizeClass(Class<?> type) {
        if (Calendar.class.isAssignableFrom(type)) {
            type = Calendar.class;
        } else if (Date.class.isAssignableFrom(type)) {
            type = Date.class;
        } else if (Value.class.isAssignableFrom(type)) {
            type = Value.class;
        } else if (Property.class.isAssignableFrom(type)) {
            type = Property.class;
        }
        return type;
    }

    private static @NotNull Map<String, Object> transformEntries(final @NotNull Map<String, JcrPropertyMapCacheEntry> map) {

        final Map<String, Object> transformedEntries = new LinkedHashMap<>(map.size());
        for (final Map.Entry<String, JcrPropertyMapCacheEntry> entry : map.entrySet())
            transformedEntries.put(entry.getKey(), entry.getValue().getPropertyValueOrNull());

        return transformedEntries;
    }

    // ---------- Map

    /**
     * @see java.util.Map#clear()
     */
    @Override
    public void clear() {
        throw new UnsupportedOperationException("clear");
    }

    /**
     * @see java.util.Map#put(java.lang.Object, java.lang.Object)
     */
    @Override
    public Object put(final String aKey, final Object value) {
        throw new UnsupportedOperationException();
    }

    /**
     * @see java.util.Map#putAll(java.util.Map)
     */
    @Override
    public void putAll(final @NotNull Map<? extends String, ? extends Object> t) {
        throw new UnsupportedOperationException();
    }

    /**
     * @see java.util.Map#remove(java.lang.Object)
     */
    @Override
    public Object remove(final Object aKey) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        if (this instanceof ModifiableValueMap) {
            sb.append("JcrModifiablePropertyMap");
        } else {
            sb.append("JcrPropertyMap");
        }
        sb.append(" [node=");
        sb.append(this.node);
        sb.append(", values={");
        final Iterator<Map.Entry<String, Object>> iter = this.entrySet().iterator();
        boolean first = true;
        while (iter.hasNext()) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            final Map.Entry<String, Object> e = iter.next();
            sb.append(e.getKey());
            sb.append("=");
            sb.append(e.getValue());
        }
        sb.append("}]");
        return sb.toString();
    }
}
