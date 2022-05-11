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

import org.apache.sling.api.resource.observation.ResourceChange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension of {@code ResourceChange} to support user id (if available)
 */
public class JcrResourceChange extends ResourceChange {

    private final String userId;

    public JcrResourceChange(@NotNull final ResourceChange.ChangeType changeType,
                             @NotNull final String path,
                             final boolean isExternal,
                             @Nullable final String userId) {
        super(changeType, path, isExternal);
        this.userId = userId;
    }

    @Override
    public @Nullable String getUserId() {
        return userId;
    }
}