package uk.gov.moj.cpp.defence.healthchecks;

import static java.util.Arrays.asList;
import static uk.gov.justice.services.healthcheck.healthchecks.FileStoreHealthcheck.FILE_STORE_HEALTHCHECK_NAME;
import static uk.gov.justice.services.healthcheck.healthchecks.JobStoreHealthcheck.JOB_STORE_HEALTHCHECK_NAME;

import uk.gov.justice.services.healthcheck.api.DefaultIgnoredHealthcheckNamesProvider;

import java.util.List;

import jakarta.enterprise.inject.Specializes;

@Specializes
public class DefenceIgnoredHealthcheckNamesProvider extends DefaultIgnoredHealthcheckNamesProvider {

    public DefenceIgnoredHealthcheckNamesProvider() {
        // This constructor is required by CDI.
    }

    @Override
    public List<String> getNamesOfIgnoredHealthChecks() {
        return asList(JOB_STORE_HEALTHCHECK_NAME, FILE_STORE_HEALTHCHECK_NAME);
    }
}