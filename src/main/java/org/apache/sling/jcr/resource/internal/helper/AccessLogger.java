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
package org.apache.sling.jcr.resource.internal.helper;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Helper class to report on repository access.
 *  
 *  The focus is to have a less overhead as possible if it is not used; in case it is enabled and access
 *  is logged, the performance overhead does not matter.
 *  
 *  In the current implementation it has 2 features:
 *  * Write a stacktrace on every recorded operation; this comes with a massive overhead and it is definitely
 *    not recommended to turn this on on production, since it can easily create 100s of megabytes of log and will slow
 *    down processing massively. To use this turn on TRACE logging on 'org.apache.sling.jcr.resource.AccessLogger.operation'
 *  * When a resource resolver is closed, dump a single log statement about the number of recorded operations. The overhead is
 *    very small and it can be turned on for a longer period of time also in production. To use it enable DEBUG logging on
 *    'org.apache.sling.jcr.resource.AccessLogger.statistics'.
 *  
 *
 */
public class AccessLogger implements Closeable {
    
    
    Map<String,AtomicLong> metrics = new HashMap<>();
    
    private static final String SELF_NAME= AccessLogger.class.getName();
    private static final Logger STATISTICS_LOG = LoggerFactory.getLogger("org.apache.sling.jcr.resource.AccessLogger.statistics");
    private static final Logger OPERATION_LOG  = LoggerFactory.getLogger("org.apache.sling.jcr.resource.AccessLogger.operation");
    
    private final ResourceResolver resolver;
    
    // public
    
    
    public static void incrementUsage(ResourceResolver resolver, String operation, String path) {
        incrementUsage(resolver,operation, path, 1);
    }
    
    public static void incrementUsage(Resource resource, String operation) {
        incrementUsage(resource.getResourceResolver(),operation, resource.getPath(), 1);
    }
    
    public static void incrementUsage(Resource resource, String operation, long count) {
        incrementUsage(resource.getResourceResolver(), operation, resource.getPath(), count);
    }
    
    public static void incrementUsage(ResourceResolver resolver, String operation, String path, long count) {

        if (STATISTICS_LOG.isDebugEnabled()) {
            AccessLogger am = (AccessLogger) resolver.getPropertyMap().get(SELF_NAME);
            if (am == null) {
                am = new AccessLogger(resolver);
            }
            am.incrementUsage(operation,count);
        }
        if (OPERATION_LOG.isTraceEnabled()) {
            try {
                String msg = String.format("invoked %s on [%s]", operation, path);
                throw new Exception(msg);
            } catch (Exception e) {
                OPERATION_LOG.trace ("AccessLogger recording", e);
            }
        }
    }
    
    // private
    
    private AccessLogger (ResourceResolver resolver) {
        this.resolver = resolver;
        resolver.getPropertyMap().put(SELF_NAME,this);
    }
    
    
    private void incrementUsage(String operation, long count) {
        AtomicLong meter = metrics.get(operation);
        if (meter == null) {
            metrics.put(operation, new AtomicLong(count));
        } else {
            meter.addAndGet(count);
        }
    }
    
    @Override
    public String toString() {
        String values = metrics.keySet().stream()
            .map(key -> key + "=" + metrics.get(key).get())
            .collect(Collectors.joining(","));
        
        return "AccessLogger (" + values + ")";
    }


    @Override
    public void close() {
        STATISTICS_LOG.debug("AccessLogger dump for ResourceResolver (userid={},tostring={}): {}", resolver.getUserID(), resolver, this);
    }
    
    
    
    
    
    

}
