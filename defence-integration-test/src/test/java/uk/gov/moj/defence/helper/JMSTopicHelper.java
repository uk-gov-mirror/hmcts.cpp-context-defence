package uk.gov.moj.defence.helper;


import static java.lang.String.format;
import static jakarta.jms.Session.AUTO_ACKNOWLEDGE;
import static uk.gov.justice.services.messaging.JsonEnvelope.envelopeFrom;
import static uk.gov.justice.services.test.utils.core.messaging.QueueUriProvider.queueUri;

import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.justice.services.messaging.Metadata;

import java.time.ZonedDateTime;
import java.util.UUID;

import jakarta.jms.Connection;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.json.JsonObject;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.commons.lang3.RandomStringUtils;

public class JMSTopicHelper implements AutoCloseable {

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String QUEUE_URI = queueUri();

    private Session session;
    private MessageProducer messageProducer;
    private Connection connection;

    public void startProducer(final String topicName) {

        try {
            final ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(QUEUE_URI);
            // Artemis 2.54 closes idle client connections; disable the idle timeout and shorten the call timeout
            // so a long-running test does not later fail with "AMQ219014: Timed out waiting for response".
            factory.setConnectionTTL(-1);
            factory.setCallTimeout(15000);
            connection = factory.createConnection();
            connection.start();

            session = connection.createSession(false, AUTO_ACKNOWLEDGE);
            final Destination destination = session.createTopic(topicName);
            messageProducer = session.createProducer(destination);
        } catch (final JMSException e) {
            close();
            throw new RuntimeException(format("Failed to create message producer to topic: '%s', queue uri: '%s'", topicName, QUEUE_URI), e);
        }
    }

    public void sendMessage(final String commandName, final JsonObject payload) {

        if (messageProducer == null) {
            close();
            throw new RuntimeException("Message producer not started. Please call startProducer(...) first.");
        }

        final Metadata metadata = createMetadataForCommandWith(commandName);

        sendMessage(commandName, payload, metadata);
    }

    public void sendEventMessage(final String commandName,
                                 final JsonObject payload,
                                 final UUID streamId,
                                 final long eventNumber) {

        if (messageProducer == null) {
            close();
            throw new RuntimeException("Message producer not started. Please call startProducer(...) first.");
        }

        final Metadata metadata = createMetadataForEventWith(commandName, streamId, eventNumber);

        sendMessage(commandName, payload, metadata);
    }

    private Metadata createMetadataForCommandWith(final String commandName) {

        return Envelope.metadataBuilder()
                .withId(UUID.randomUUID())
                .withName(commandName)
                .createdAt(ZonedDateTime.now())
                .withUserId(USER_ID)
                .withClientCorrelationId(UUID.randomUUID().toString())
                .withSource(RandomStringUtils.randomAlphanumeric(10))
                .build();
    }

    private Metadata createMetadataForEventWith(final String commandName, final UUID streamId, final long eventNumber) {

        return Envelope.metadataBuilder()
                .withId(UUID.randomUUID())
                .withName(commandName)
                .createdAt(ZonedDateTime.now())
                .withUserId(USER_ID)
                .withClientCorrelationId(UUID.randomUUID().toString())
                .withSource(RandomStringUtils.randomAlphanumeric(10))
                .withStreamId(streamId)
                .withPosition(1)
                // TODO: This needs fixing. we should not be generating the private event manually through tests (instead trigger the behaviour by dropping a public event or issuing a REST command
                .withPreviousEventNumber(eventNumber - 1)
                .withEventNumber(eventNumber)
                .build();
    }

    private void sendMessage(final String commandName, final JsonObject payload, final Metadata metadata) {

        final JsonEnvelope jsonEnvelope = envelopeFrom(metadata, payload);
        final String json = jsonEnvelope.toDebugStringPrettyPrint();

        try {
            final TextMessage message = session.createTextMessage();

            message.setText(json);
            message.setStringProperty("CPPNAME", commandName);

            messageProducer.send(message);
        } catch (JMSException e) {
            close();
            throw new RuntimeException("Failed to send message. commandName: '" + commandName + "', json: " + json, e);
        }
    }


    @Override
    public void close() {
        close(messageProducer);
        close(session);
        close(connection);

        session = null;
        messageProducer = null;
        connection = null;
    }

    private void close(final AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

}
