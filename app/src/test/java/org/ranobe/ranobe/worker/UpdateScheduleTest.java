package org.ranobe.ranobe.worker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class UpdateScheduleTest {
    private static Calendar at(String zone, int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(zone));
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, second);
        return calendar;
    }

    private static Calendar utc(int hour, int minute, int second) {
        return at("UTC", 2026, 10, 9, hour, minute, second);
    }

    private static String clock(Calendar calendar) {
        return String.format("%02d:%02d", calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
    }

    @Test
    public void thereAreSixDailyRunsInOrder() {
        assertEquals(6, UpdateSchedule.RUN_MINUTES.length);
        assertEquals(30, UpdateSchedule.RUN_MINUTES[0]);        // 12:30 AM
        assertEquals(480, UpdateSchedule.RUN_MINUTES[1]);       // 8:00 AM
        assertEquals(630, UpdateSchedule.RUN_MINUTES[2]);       // 10:30 AM
        assertEquals(750, UpdateSchedule.RUN_MINUTES[3]);       // 12:30 PM
        assertEquals(1200, UpdateSchedule.RUN_MINUTES[4]);      // 8:00 PM
        assertEquals(1350, UpdateSchedule.RUN_MINUTES[5]);      // 10:30 PM
        for (int i = 1; i < UpdateSchedule.RUN_MINUTES.length; i++) {
            assertTrue(UpdateSchedule.RUN_MINUTES[i] > UpdateSchedule.RUN_MINUTES[i - 1]);
        }
    }

    @Test
    public void eachRunIsFollowedByTheNextOne() {
        assertEquals("08:00", clock(UpdateSchedule.nextRun(utc(0, 30, 0))));
        assertEquals("10:30", clock(UpdateSchedule.nextRun(utc(8, 0, 0))));
        assertEquals("12:30", clock(UpdateSchedule.nextRun(utc(10, 30, 0))));
        assertEquals("20:00", clock(UpdateSchedule.nextRun(utc(12, 30, 0))));
        assertEquals("22:30", clock(UpdateSchedule.nextRun(utc(20, 0, 0))));
        assertEquals("00:30", clock(UpdateSchedule.nextRun(utc(22, 30, 0))));
    }

    @Test
    public void aMomentBeforeARunWaitsForThatRun() {
        assertEquals("08:00", clock(UpdateSchedule.nextRun(utc(7, 59, 59))));
        assertEquals(1000L, UpdateSchedule.millisUntilNext(utc(7, 59, 59)));
        assertEquals("00:30", clock(UpdateSchedule.nextRun(utc(0, 29, 59))));
        assertEquals("10:30", clock(UpdateSchedule.nextRun(utc(10, 29, 0))));
    }

    @Test
    public void thePastMidnightRunBelongsToTheNextDay() {
        Calendar next = UpdateSchedule.nextRun(utc(23, 59, 0));

        assertEquals("00:30", clock(next));
        assertEquals(10, next.get(Calendar.DAY_OF_MONTH));
        assertEquals(TimeUnit.MINUTES.toMillis(31), UpdateSchedule.millisUntilNext(utc(23, 59, 0)));
        assertEquals(TimeUnit.MINUTES.toMillis(30), UpdateSchedule.millisUntilNext(utc(0, 0, 0)));
    }

    @Test
    public void theGapsBetweenRunsMatchTheTimesAsked() {
        assertEquals(TimeUnit.MINUTES.toMillis(450), UpdateSchedule.millisUntilNext(utc(0, 30, 0)));   // to 8:00
        assertEquals(TimeUnit.MINUTES.toMillis(150), UpdateSchedule.millisUntilNext(utc(8, 0, 0)));    // to 10:30
        assertEquals(TimeUnit.MINUTES.toMillis(120), UpdateSchedule.millisUntilNext(utc(10, 30, 0)));  // to 12:30
        assertEquals(TimeUnit.MINUTES.toMillis(450), UpdateSchedule.millisUntilNext(utc(12, 30, 0)));  // to 20:00
        assertEquals(TimeUnit.MINUTES.toMillis(150), UpdateSchedule.millisUntilNext(utc(20, 0, 0)));   // to 22:30
        assertEquals(TimeUnit.MINUTES.toMillis(120), UpdateSchedule.millisUntilNext(utc(22, 30, 0)));  // to 00:30
    }

    @Test
    public void theNewYearIsHandled() {
        Calendar next = UpdateSchedule.nextRun(at("UTC", 2026, 12, 31, 23, 0, 0));

        assertEquals(2027, next.get(Calendar.YEAR));
        assertEquals(Calendar.JANUARY, next.get(Calendar.MONTH));
        assertEquals(1, next.get(Calendar.DAY_OF_MONTH));
        assertEquals("00:30", clock(next));
    }

    @Test
    public void runsStayOnTheWallClockWhenTheClocksSpringForward() {
        // 2026-03-08 in New York: 02:00 jumps to 03:00, so 01:00 to 08:00 is 6 real hours, not 7
        Calendar now = at("America/New_York", 2026, 3, 8, 1, 0, 0);

        assertEquals("08:00", clock(UpdateSchedule.nextRun(now)));
        assertEquals(TimeUnit.HOURS.toMillis(6), UpdateSchedule.millisUntilNext(now));
    }

    @Test
    public void runsStayOnTheWallClockWhenTheClocksFallBack() {
        // 2026-11-01 in New York: 02:00 goes back to 01:00, so 00:45 to 08:00 is 8h15m of real time, not 7h15m
        Calendar now = at("America/New_York", 2026, 11, 1, 0, 45, 0);

        assertEquals("08:00", clock(UpdateSchedule.nextRun(now)));
        assertEquals(TimeUnit.MINUTES.toMillis(8 * 60 + 15), UpdateSchedule.millisUntilNext(now));
    }

    @Test
    public void theWaitIsNeverZeroOrNegative() {
        for (int hour = 0; hour < 24; hour++) {
            for (int minute = 0; minute < 60; minute += 15) {
                assertTrue(UpdateSchedule.millisUntilNext(utc(hour, minute, 0)) > 0);
                assertTrue(UpdateSchedule.millisUntilNext(utc(hour, minute, 0)) <= TimeUnit.HOURS.toMillis(8));
            }
        }
    }
}
