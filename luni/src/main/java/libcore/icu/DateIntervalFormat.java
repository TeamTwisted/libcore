/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package libcore.icu;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.ULocale;

import java.text.FieldPosition;
import java.util.TimeZone;
import libcore.util.BasicLruCache;

import static libcore.icu.DateUtilsBridge.FORMAT_UTC;

/**
 * Exposes icu4j's DateIntervalFormat.
 */
public final class DateIntervalFormat {

  private static final FormatterCache CACHED_FORMATTERS = new FormatterCache();

  static class FormatterCache extends BasicLruCache<String, com.ibm.icu.text.DateIntervalFormat> {
    FormatterCache() {
      super(8);
    }
  }

  private DateIntervalFormat() {
  }

  // This is public DateUtils API in frameworks/base.
  public static String formatDateRange(long startMs, long endMs, int flags, String olsonId) {
    if ((flags & FORMAT_UTC) != 0) {
      olsonId = "UTC";
    }
    // We create a java.util.TimeZone here to use libcore's data and libcore's olson ID / pseudo-tz
    // logic.
    TimeZone tz = (olsonId != null) ? TimeZone.getTimeZone(olsonId) : TimeZone.getDefault();
    com.ibm.icu.util.TimeZone icuTimeZone = DateUtilsBridge.icuTimeZone(tz);
    ULocale icuLocale = ULocale.getDefault();
    return formatDateRange(icuLocale, icuTimeZone, startMs, endMs, flags);
  }

  // This is our slightly more sensible internal API. (A truly sane replacement would take a
  // skeleton instead of int flags.)
  public static String formatDateRange(ULocale icuLocale, com.ibm.icu.util.TimeZone icuTimeZone,
      long startMs, long endMs, int flags) {
    Calendar startCalendar = DateUtilsBridge.createIcuCalendar(icuTimeZone, icuLocale, startMs);
    Calendar endCalendar;
    if (startMs == endMs) {
      endCalendar = startCalendar;
    } else {
      endCalendar = DateUtilsBridge.createIcuCalendar(icuTimeZone, icuLocale, endMs);
    }

    boolean endsAtMidnight = isMidnight(endCalendar);

    // If we're not showing the time or the start and end times are on the same day, and the
    // end time is midnight, fudge the end date so we don't count the day that's about to start.
    // This is not the behavior of icu4j's DateIntervalFormat, but it's the historical behavior
    // of Android's DateUtils.formatDateRange.
    if (startMs != endMs && endsAtMidnight &&
        ((flags & DateUtilsBridge.FORMAT_SHOW_TIME) == 0
            || DateUtilsBridge.dayDistance(startCalendar, endCalendar) <= 1)) {
      endCalendar.add(Calendar.DAY_OF_MONTH, -1);
    }

    String skeleton = DateUtilsBridge.toSkeleton(startCalendar, endCalendar, flags);
    synchronized (CACHED_FORMATTERS) {
      com.ibm.icu.text.DateIntervalFormat formatter =
          getFormatter(skeleton, icuLocale, icuTimeZone);
      return formatter.format(startCalendar, endCalendar, new StringBuffer(),
          new FieldPosition(0)).toString();
    }
  }

  private static com.ibm.icu.text.DateIntervalFormat getFormatter(String skeleton, ULocale locale,
      com.ibm.icu.util.TimeZone icuTimeZone) {
    String key = skeleton + "\t" + locale + "\t" + icuTimeZone;
    com.ibm.icu.text.DateIntervalFormat formatter = CACHED_FORMATTERS.get(key);
    if (formatter != null) {
      return formatter;
    }
    formatter = com.ibm.icu.text.DateIntervalFormat.getInstance(skeleton, locale);
    formatter.setTimeZone(icuTimeZone);
    CACHED_FORMATTERS.put(key, formatter);
    return formatter;
  }

  private static boolean isMidnight(Calendar c) {
    return c.get(Calendar.HOUR_OF_DAY) == 0 &&
        c.get(Calendar.MINUTE) == 0 &&
        c.get(Calendar.SECOND) == 0 &&
        c.get(Calendar.MILLISECOND) == 0;
  }

}
