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
package org.apache.sling.jcr.resource.api;

import org.apache.sling.api.resource.observation.ResourceChange;

/**
 * Extension of {@code ResourceChange} to support user id and user data (if available)
 */
public final class JcrResourceChange extends ResourceChange {

    private final String userId;
    private final String userData;

    public JcrResourceChange(final ResourceChange.ChangeType changeType,
                             final String path,
                             final boolean isExternal,
                             final String userId, String userData) {
        super(changeType, path, isExternal);
        this.userId = userId;
        this.userData = userData;
    }

    @Override
    public String getUserId() {
        return userId;
    }

    /**
     * Get the user data associated with the underlying JCR observation event ({@link javax.jcr.observation.Event#getUserData()})
     * @return the JCR observation event's user data (may be {@code null})
     */
    public String getUserData() {
        return userData;
    }
}