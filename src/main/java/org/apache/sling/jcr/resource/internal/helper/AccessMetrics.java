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
 *  PoJo to hold access metrics for a ResourceResolver; it is designed
 *  for analysis of access patterns in lab situations; therefor its main
 *  goal is to have very low overhead in case it is not used.
 *  
 *  this class is not threadsafe!
 *  
 *  This class implements autocloseable, so the statistics are written when the RR
 *  is closed (see ResourceResolver.getPropertyMap)
 *  
 *
 */
public class AccessMetrics implements Closeable {
    
    
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
            AccessMetrics am = (AccessMetrics) resolver.getPropertyMap().get(SELF_NAME);
            if (am == null) {
                am = new AccessMetrics(resolver);
            }
            am.incrementUsage(operation,count);
        }
        if (OPERATION_LOG.isTraceEnabled()) {
            try {
                String msg = String.format("invoked %s on [%s]", operation, path);
                throw new Exception(msg);
            } catch (Exception e) {
                OPERATION_LOG.trace ("AccessMetric logging", e);
            }
        }
    }
    
    // private
    
    Map<String,AtomicLong> metrics = new HashMap<>();
    
    private static final String SELF_NAME= AccessMetrics.class.getName();
    private static final Logger STATISTICS_LOG = LoggerFactory.getLogger("org.apache.sling.jcr.resource.accessMetrics.statistics");
    private static final Logger OPERATION_LOG  = LoggerFactory.getLogger("org.apache.sling.jcr.resource.accessMetrics.operation");
    
    private final ResourceResolver resolver;
    
    
    private AccessMetrics (ResourceResolver resolver) {
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
        
        return "AccessMetrics (" + values + ")";
    }


    @Override
    public void close() {
        STATISTICS_LOG.debug("AccessMetric dump for ResourceResolver (userid={},tostring={}): {}", resolver.getUserID(), resolver, this);
    }
    
    
    
    
    
    

}
