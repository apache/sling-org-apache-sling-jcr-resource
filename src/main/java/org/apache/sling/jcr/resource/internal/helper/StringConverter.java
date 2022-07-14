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

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;

import org.apache.jackrabbit.util.ISO8601;
import org.jetbrains.annotations.NotNull;

/**
 * A converter for any object based on toString()
 */
public class StringConverter implements Converter {

    private final Object value;

    public StringConverter(final Object val) {
        this.value = val;
    }

    /**
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return this.value.toString();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toLong()
     */
    public @NotNull Long toLong() {
        return Long.parseLong(this.toString());
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toByte()
     */
    public @NotNull Byte toByte() {
        return Byte.parseByte(this.toString());
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toShort()
     */
    public @NotNull Short toShort() {
        return Short.parseShort(this.toString());
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toInteger()
     */
    public @NotNull Integer toInteger() {
        return Integer.parseInt(this.toString());
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toDouble()
     */
    public @NotNull Double toDouble() {
        return Double.parseDouble(this.toString());
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toFloat()
     */
    public @NotNull Float toFloat() {
        return Float.parseFloat(this.toString());
    }

    @Override
    public @NotNull ZonedDateTime toZonedDateTime() {
        return new CalendarConverter(toCalendar()).toZonedDateTime();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toCalendar()
     */
    public @NotNull Calendar toCalendar() {
        final Calendar c = ISO8601.parse(toString());
        if (c == null) {
            throw new IllegalArgumentException("Not a date string: " + toString());
        }
        return c;
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toDate()
     */
    public @NotNull Date toDate() {
        final Calendar c = this.toCalendar();
        return c.getTime();
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toBoolean()
     */
    public @NotNull Boolean toBoolean() {
        return Boolean.valueOf(this.toString());
    }

    /**
     * @see org.apache.sling.jcr.resource.internal.helper.Converter#toBigDecimal()
     */
    public @NotNull BigDecimal toBigDecimal() {
        return new BigDecimal(this.toString());
    }
}
