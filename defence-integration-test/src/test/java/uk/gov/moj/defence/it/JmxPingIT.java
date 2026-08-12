package uk.gov.moj.defence.it;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.justice.services.jmx.api.domain.CommandState.COMMAND_COMPLETE;
import static uk.gov.justice.services.jmx.api.mbean.CommandRunMode.FORCED;
import static uk.gov.justice.services.jmx.system.command.client.connection.JmxParametersBuilder.jmxParameters;
import static uk.gov.justice.services.management.ping.commands.PingCommand.PING;
import static uk.gov.justice.services.test.utils.common.host.TestHostProvider.getHost;

import uk.gov.justice.services.jmx.api.domain.SystemCommandStatus;
import uk.gov.justice.services.jmx.api.mbean.SystemCommanderMBean;
import uk.gov.justice.services.jmx.system.command.client.SystemCommanderClient;
import uk.gov.justice.services.jmx.system.command.client.TestSystemCommanderClientFactory;
import uk.gov.justice.services.jmx.system.command.client.connection.JmxParameters;
import uk.gov.justice.services.test.utils.core.messaging.Poller;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

public class JmxPingIT {

    private static final String HOST = getHost();
    private static final int PORT = 9990;
    private static final String CONTEXT = "defence";
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";

    private static final UUID NULL_COMMAND_RUNTIME_ID = null;
    private static final String NULL_COMMAND_RUNTIME_STRING = null;

    // Poll for up to ~10s (200 x 50ms) for the asynchronous PING command to complete.
    private static final int POLL_RETRY_COUNT = 200;
    private static final long POLL_INTERVAL_MILLIS = 50L;

    private final TestSystemCommanderClientFactory systemCommanderClientFactory = new TestSystemCommanderClientFactory();

    @Test
    public void shouldSuccessfullyCallPingUsingJmx() {

        final JmxParameters jmxParameters = jmxParameters()
                .withContextName(CONTEXT)
                .withHost(HOST)
                .withPort(PORT)
                .withUsername(USERNAME)
                .withPassword(PASSWORD)
                .build();
        try (final SystemCommanderClient systemCommanderClient = systemCommanderClientFactory.create(jmxParameters)) {
            final SystemCommanderMBean systemCommanderMBean = systemCommanderClient.getRemote(CONTEXT);
            final UUID commandId = systemCommanderMBean.call(
                    PING,
                    NULL_COMMAND_RUNTIME_ID,
                    NULL_COMMAND_RUNTIME_STRING,
                    FORCED.isGuarded());

            assertThat(commandId, is(notNullValue()));

            // PING runs asynchronously, so poll for completion rather than reading the status
            // once - a single immediate read races the command and observes COMMAND_IN_PROGRESS
            // on WildFly 40 / Java 25. Poller (not awaitility) is used deliberately so failures
            // surface the actual last-observed state rather than being hidden.
            final Poller poller = new Poller(POLL_RETRY_COUNT, POLL_INTERVAL_MILLIS);
            final Optional<SystemCommandStatus> completed = poller.pollUntilFound(() -> {
                final SystemCommandStatus status = systemCommanderMBean.getCommandStatus(commandId);
                return status.getCommandState() == COMMAND_COMPLETE
                        ? Optional.of(status)
                        : Optional.<SystemCommandStatus>empty();
            });

            // On timeout re-read the status so the failure message reports the actual last state.
            final SystemCommandStatus commandStatus = completed
                    .orElseGet(() -> systemCommanderMBean.getCommandStatus(commandId));
            assertThat(commandStatus.getCommandId(), is(commandId));
            assertThat(
                    String.format("PING command %s did not reach %s within %d x %dms polls - last observed state was %s",
                            commandId, COMMAND_COMPLETE, POLL_RETRY_COUNT, POLL_INTERVAL_MILLIS, commandStatus.getCommandState()),
                    commandStatus.getCommandState(), is(COMMAND_COMPLETE));
        }
    }
}
