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
package org.apache.sling.jcr.resource.internal.helper;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

/**
 * A converter for Boolean
 */
public class BooleanConverter implements Converter {

    private final Boolean value;

    public BooleanConverter(@NotNull final Boolean val) {
        this.value = val;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return this.value.toString();
    }

    private @NotNull Integer getNumber() {
        return ( value.booleanValue() ? 1 : 0);
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toLong()
     */
    public @NotNull Long toLong() {
        return this.getNumber().longValue();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toByte()
     */
    public @NotNull Byte toByte() {
        return this.getNumber().byteValue();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toShort()
     */
    public @NotNull Short toShort() {
        return this.getNumber().shortValue();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toInteger()
     */
    public @NotNull Integer toInteger() {
        return getNumber().intValue();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toDouble()
     */
    public @NotNull Double toDouble() {
        return this.getNumber().doubleValue();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toFloat()
     */
    public @NotNull Float toFloat() {
        return this.getNumber().floatValue();
    }

    @Override
    public @NotNull ZonedDateTime toZonedDateTime() {
        return new CalendarConverter(toCalendar()).toZonedDateTime();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toCalendar()
     */
    public @NotNull Calendar toCalendar() {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(this.toLong());
        return c;
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toDate()
     */
    public @NotNull Date toDate() {
        return new Date(this.toLong());
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toBoolean()
     */
    public @NotNull Boolean toBoolean() {
        return this.value;
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toBigDecimal()
     */
    public @NotNull BigDecimal toBigDecimal() {
        return new BigDecimal(this.getNumber().toString());
    }
}
