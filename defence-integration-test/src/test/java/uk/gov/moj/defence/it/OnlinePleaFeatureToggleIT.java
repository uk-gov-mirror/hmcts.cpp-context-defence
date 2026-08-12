package uk.gov.moj.defence.it;

import static com.google.common.collect.ImmutableMap.of;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.UUID.randomUUID;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static uk.gov.moj.cpp.platform.test.feature.toggle.FeatureStubber.stubFeaturesFor;
import static uk.gov.moj.defence.util.PleaAccessControl.mockDefenceUserPleaAccessControl;
import static uk.gov.moj.defence.util.ProsecutionCaseQueryStub.stubForProsecutionCaseQuery;
import static uk.gov.moj.defence.util.RestHelper.pollForResponse;
import static uk.gov.moj.defence.util.UsersGroupStub.stubUsersGroupsPermission;

import java.util.UUID;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class OnlinePleaFeatureToggleIT {
    private static final String OPA_ELIGIBLE_URL = "/onlineplea/%s";
    private static final String MEDIA_TYPE = "application/vnd.defence.query.eligible-for-online-plea+json";
    public static final String DEFENCE_CONTEXT = "defence";
    public static final String FEATURE = "OPA";
    public static final String userId = randomUUID().toString();

    @BeforeAll
    public static void init() {
        mockDefenceUserPleaAccessControl(userId);
        stubUsersGroupsPermission();
    }

    @Test
    public void shouldAllowPerformEligibleForOnlinePleaQueryWhenToggleOn() {
        subFeatureToggle(true);
        stubForProsecutionCaseQuery();

        final UUID caseId = randomUUID();
        pollForResponse(format(OPA_ELIGIBLE_URL, caseId), MEDIA_TYPE, userId, emptyList());
    }

    @Test
    public void shouldNotAllowEligibleForOnlinePleaQueryWhenToggleOff() {
        subFeatureToggle(false);

        final UUID caseId = randomUUID();
        pollForResponse(format(OPA_ELIGIBLE_URL, caseId), MEDIA_TYPE, userId, emptyList(), FORBIDDEN);
    }

    private void subFeatureToggle(boolean toggle) {
        final ImmutableMap<String, Boolean> features = of(FEATURE, toggle);
        stubFeaturesFor(DEFENCE_CONTEXT, features);
    }
}
