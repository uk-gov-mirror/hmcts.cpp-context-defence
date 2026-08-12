package uk.gov.moj.cpp.defence.event.service;

import static uk.gov.justice.services.messaging.JsonObjects.createObjectBuilder;
import static jakarta.transaction.Transactional.TxType.REQUIRES_NEW;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.justice.services.core.dispatcher.SystemUserProvider;
import uk.gov.justice.services.fileservice.api.FileServiceException;
import uk.gov.justice.services.fileservice.api.FileStorer;
import uk.gov.justice.services.messaging.JsonEnvelope;
import uk.gov.moj.cpp.defence.event.exception.FileUploadException;
import uk.gov.moj.cpp.defence.event.exception.OpaDocumentGenerationException;
import uk.gov.moj.cpp.defence.event.exception.UserNotFoundException;
import uk.gov.moj.cpp.system.documentgenerator.client.DocumentGeneratorClientProducer;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.transaction.Transactional;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@SuppressWarnings({"squid:S00107", "squid:S2139", "squid:S00112"})
public class DocumentGeneratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentGeneratorService.class);
    private static final String ERROR_MESSAGE = "Error while uploading document generation or upload ";

    private final DocumentGeneratorClientProducer documentGeneratorClientProducer;

    private final FileStorer fileStorer;

    private final SystemUserProvider systemUserProvider;

    private final MaterialService materialService;

    @Inject
    public DocumentGeneratorService(final SystemUserProvider systemUserProvider,
                                    final DocumentGeneratorClientProducer documentGeneratorClientProducer,
                                    final FileStorer fileStorer,
                                    final MaterialService materialService) {
        this.systemUserProvider = systemUserProvider;
        this.documentGeneratorClientProducer = documentGeneratorClientProducer;
        this.fileStorer = fileStorer;
        this.materialService = materialService;
    }

    @Transactional(REQUIRES_NEW)
    public String generateOpaDocument(final JsonEnvelope envelope, final JsonObject documentPayload, String templateName, final UUID materialId, final String pdfFileName) {

        final String fileName = pdfFileName + ".pdf";
        try {
            final byte[] resultOrderAsByteArray = documentGeneratorClientProducer.documentGeneratorClient().generatePdfDocument(documentPayload, templateName, getSystemUserUuid());
            addDocumentToMaterial(
                    envelope,
                    fileName,
                    new ByteArrayInputStream(resultOrderAsByteArray),
                    materialId);

        } catch (IOException e) {
            LOGGER.error(ERROR_MESSAGE, e);
            throw new OpaDocumentGenerationException("Error while generating OPA document", e);
        }
        return fileName;
    }

    private void addDocumentToMaterial(final JsonEnvelope originatingEnvelope, final String filename, final InputStream fileContent, final UUID materialId) {

        try {
            final UUID fileId = storeFile(fileContent, filename);
            LOGGER.info("Stored material {} in file store {}", materialId, fileId);
            materialService.uploadMaterial(fileId, materialId, originatingEnvelope);
        } catch (final FileServiceException e) {
            LOGGER.error("Error while uploading file {}", filename);
            throw new FileUploadException(e);
        }
    }

    private UUID storeFile(final InputStream fileContent, final String fileName) throws FileServiceException {
        final JsonObject metadata = createObjectBuilder().add("fileName", fileName).build();
        return fileStorer.store(metadata, fileContent);
    }

    private UUID getSystemUserUuid() {
        return systemUserProvider.getContextSystemUserId().orElseThrow(() -> new UserNotFoundException("System user id not find"));
    }

}
