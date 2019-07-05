package org.apache.sling.jcr.resource.internal.helper;

import java.time.ZonedDateTime;
import java.util.GregorianCalendar;

public class ZonedDateTimeConverter extends CalendarConverter {

    public ZonedDateTimeConverter(ZonedDateTime value) {
        super(GregorianCalendar.from(value));
    }
}
