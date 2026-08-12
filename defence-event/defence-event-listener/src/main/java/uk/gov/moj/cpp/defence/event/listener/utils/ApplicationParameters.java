package uk.gov.moj.cpp.defence.event.listener.utils;

import static java.lang.Integer.parseInt;

import uk.gov.justice.services.common.configuration.Value;

import jakarta.inject.Inject;

public class ApplicationParameters {

    @Inject
    @Value(key = "defence.case.assignment.expiry.hours", defaultValue = "150")
    private String assignmentExpiryHoursValue;

    public String getAssignmentExpiryHoursValue() {
        return assignmentExpiryHoursValue;
    }

    public int getAssignmentExpiryHours() {
        return parseInt(assignmentExpiryHoursValue);
    }
}
