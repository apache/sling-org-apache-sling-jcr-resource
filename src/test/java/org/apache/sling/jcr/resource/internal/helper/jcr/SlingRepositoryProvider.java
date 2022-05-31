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

import java.lang.reflect.Method;

import org.apache.sling.jcr.api.SlingRepository;
import org.apache.sling.testing.mock.sling.oak.OakMockSlingRepository;
import org.mockito.Mockito;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.ComponentContext;

public class SlingRepositoryProvider {

    private static OakMockSlingRepository INSTANCE;

    private SlingRepositoryProvider() {
    }

    public synchronized static SlingRepository getRepository() throws Exception {
        if (INSTANCE == null) {
            OakMockSlingRepository r = new OakMockSlingRepository();
            Method activateMethod = OakMockSlingRepository.class.getDeclaredMethod("activate", BundleContext.class);
            activateMethod.setAccessible(true);
            activateMethod.invoke(r, getFakeContext());
            INSTANCE = r;
        }
        return INSTANCE;
    }


    public static void shutdown() throws Exception {
        Method deactivateMethod = OakMockSlingRepository.class.getDeclaredMethod("deactivate", ComponentContext.class);
        deactivateMethod.setAccessible(true);
        deactivateMethod.invoke(getRepository(), (ComponentContext) null);
    }


    private static BundleContext getFakeContext() {
        return Mockito.mock(BundleContext.class);
    }
}
