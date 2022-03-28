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

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.jcr.RepositoryException;
import javax.jcr.query.QueryResult;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.sling.jcr.resource.internal.helper.JcrResourceUtil;
import org.apache.sling.spi.resource.provider.ProviderContext;
import org.apache.sling.spi.resource.provider.ResolveContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LimitingQueryLanguageProvider extends BasicQueryLanguageProvider {

    private final Logger log = LoggerFactory.getLogger(LimitingQueryLanguageProvider.class);

    /** The limit to set for queries */
    private final long defaultLimit;

    public LimitingQueryLanguageProvider(final ProviderContext ctx, long defaultLimit) {
        super(ctx);
        this.defaultLimit = defaultLimit;
    }

    @Override
    protected QueryResult query(final ResolveContext<JcrProviderState> ctx, final String query, final String language)
            throws RepositoryException {
        Triple<String, Long, Long> settings = extractQuerySettings(query);
        return JcrResourceUtil.query(ctx.getProviderState().getSession(), settings.getLeft(), language,
                settings.getMiddle(), settings.getRight());
    }

    protected Triple<String, Long, Long> extractQuerySettings(String query) {
        query = query.trim();
        if (query.endsWith("*/")) {
            Pair<Long, Long> settings = parseQueryComment(
                    query.substring(query.lastIndexOf("/*") + 2, query.lastIndexOf("*/")));
            return new ImmutableTriple<>(query.substring(0, query.lastIndexOf("/*")),
                    settings.getLeft(), settings.getRight());
        } else {
            return new ImmutableTriple<>(query, 0L, defaultLimit);
        }
    }

    private Pair<Long, Long> parseQueryComment(String query) {
        Map<String, Object> parsed = new HashMap<>();
        StreamTokenizer tokenizer = new StreamTokenizer(new StringReader(query));
        int currentToken;
        try {
            currentToken = tokenizer.nextToken();
            boolean key = true;
            Object current = null;
            while (currentToken != StreamTokenizer.TT_EOF) {
                if (tokenizer.ttype == StreamTokenizer.TT_NUMBER) {
                    if (!key) {
                        parsed.put((String) current, tokenizer.nval);
                        key = true;
                        current = null;
                    } else {
                        throw new IOException(
                                "Encountered unexpected numeric key: " + tokenizer.toString());
                    }
                } else if (tokenizer.ttype == StreamTokenizer.TT_WORD) {
                    if (!key) {
                        parsed.put((String) current, tokenizer.nval);
                        key = true;
                    } else if (current == null) {
                        current = tokenizer.sval;
                    } else {
                        throw new IOException(
                                "Encountered unmatched key value pair: " + tokenizer.toString());
                    }
                } else if (((char) currentToken) == '=') {
                    key = false;
                } else if (((char) currentToken) == ',' || ((char) currentToken) == ';') {
                    // nothing really required, just ignoring as it's a separator
                } else {
                    throw new IOException(
                            "Encountered unexpected character parsing query comment: " + tokenizer.toString());
                }
                currentToken = tokenizer.nextToken();
            }
        } catch (Exception e) {
            log.warn("Failed to parse query comment due to exception: {}", e.toString());
            return new ImmutablePair<>(0L, defaultLimit);
        }
        return new ImmutablePair<>(getKeyAsLong(parsed, "slingQueryStart", 0L),
                getKeyAsLong(parsed, "slingQueryLimit", defaultLimit));
    }

    private Long getKeyAsLong(Map<String, Object> parsed, String key, Long defaultVal) {
        return Optional.ofNullable(parsed.get(key)).map(v -> {
            if (v instanceof String) {
                return Long.parseLong(v.toString());
            } else {
                return ((Double) v).longValue();
            }
        }).orElse(defaultVal);
    }

}
