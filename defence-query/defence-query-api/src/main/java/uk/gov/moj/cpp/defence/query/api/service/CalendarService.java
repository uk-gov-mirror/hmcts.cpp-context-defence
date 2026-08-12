package uk.gov.moj.cpp.defence.query.api.service;

import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;

import uk.gov.justice.services.core.requester.Requester;

import java.time.LocalDate;
import java.util.List;

import jakarta.inject.Inject;

public class CalendarService {

    public static final String ENGLAND_AND_WALES_DIVISION = "england-and-wales";

    @Inject
    private RefDataService referenceDataService;

    public long daysBetweenExcludeHolidays(final LocalDate date1, final LocalDate date2, final Requester requester) {
        final List<LocalDate> publicHolidaysList = referenceDataService.getPublicHolidays(ENGLAND_AND_WALES_DIVISION, date1, date1.plusDays(30), requester);

        int daysBetween = 0;
        LocalDate adjustedLocalDate = date1;
        while (adjustedLocalDate.isBefore(date2)) {
            if (!isDateOnAWeekend(adjustedLocalDate) && !publicHolidaysList.contains(adjustedLocalDate)) {
                daysBetween++;
            }
            adjustedLocalDate = adjustedLocalDate.plusDays(1);
        }
        return daysBetween;
    }


    private boolean isDateOnAWeekend(final LocalDate localDate) {
        return localDate.getDayOfWeek() == SUNDAY || localDate.getDayOfWeek() == SATURDAY;
    }
}
