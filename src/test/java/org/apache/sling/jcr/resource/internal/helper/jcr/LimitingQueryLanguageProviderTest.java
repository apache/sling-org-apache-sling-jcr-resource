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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Collection;

import org.apache.commons.lang3.tuple.Triple;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LimitingQueryLanguageProviderTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> testCases() {
        return Arrays.asList(new Object[][] {
                testCase("JCR-SQL2 No Settings", "SELECT * FROM [nt:folder]", 10L,
                        "SELECT * FROM [nt:folder]",
                        0L, 10L),
                testCase("JCR-SQL2 With Limit", "SELECT * FROM [nt:folder] /* slingQueryLimit=20 */", 10L,
                        "SELECT * FROM [nt:folder] ",
                        0L, 20L),
                testCase("JCR-SQL2 With Limit", "SELECT * FROM [nt:folder] /* slingQueryStart=2, slingQueryLimit=20 */",
                        10L,
                        "SELECT * FROM [nt:folder] ",
                        2L, 20L),
                testCase("JCR-SQL2 With Limit", "SELECT * FROM [nt:folder] /* someotherkey=2, slingQueryLimit=20 */",
                        10L,
                        "SELECT * FROM [nt:folder] ",
                        0L, 20L),
                testCase("JCR-SQL2 With Limit", "SELECT * FROM [nt:folder] /* someotherkey=2, slingQueryLimit=20 */",
                        10L,
                        "SELECT * FROM [nt:folder] ",
                        0L, 20L),
                testCase("XPath With Limit",
                        " /jcr:root/content//element(*, sling:Folder)[@sling:resourceType='x'] /* slingQueryStart=2, slingQueryLimit=20 */",
                        10L,
                        "/jcr:root/content//element(*, sling:Folder)[@sling:resourceType='x'] ",
                        2L, 20L),
                testCase("XPath With Invalid Key",
                        " /jcr:root/content//element(*, sling:Folder)[@sling:resourceType='x'] /* 2=2, slingQueryLimit=20 */",
                        10L,
                        "/jcr:root/content//element(*, sling:Folder)[@sling:resourceType='x'] ",
                        0L, 10L)
        });
    }

    public static Object[] testCase(String name, String query, long defaultLimit, String expectedQuery,
            long expectedStart,
            long expectedLimit) {
        return new Object[] {
                name, query, defaultLimit, expectedQuery, expectedStart, expectedLimit
        };

    }

    private String name;
    private String query;
    private Long defaultLimit;
    private String expectedQuery;
    private Long expectedStart;
    private Long expectedLimit;

    public LimitingQueryLanguageProviderTest(String name, String query, long defaultLimit,
            String expectedQuery,
            long expectedStart,
            long expectedLimit) {
        this.name = name;
        this.query = query;
        this.defaultLimit = defaultLimit;
        this.expectedQuery = expectedQuery;
        this.expectedStart = expectedStart;
        this.expectedLimit = expectedLimit;
    }

    @Test
    public void testQueryLanguageProvider() {
        LimitingQueryLanguageProvider provider = new LimitingQueryLanguageProvider(mock(ProviderContext.class),
                defaultLimit);

        Triple<String, Long, Long> settings = provider.extractQuerySettings(query);

        assertEquals(expectedQuery, settings.getLeft());
        assertEquals(expectedStart, settings.getMiddle());
        assertEquals(expectedLimit, settings.getRight());

    }

}
