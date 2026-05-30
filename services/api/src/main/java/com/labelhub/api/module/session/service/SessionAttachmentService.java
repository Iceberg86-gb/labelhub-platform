package com.labelhub.api.module.session.service;

import com.labelhub.api.generated.model.UploadedFile;
import com.labelhub.api.module.export.storage.ObjectStorageWriter;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.exception.InvalidSessionAttachmentException;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SessionAttachmentService {

    private static final long MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L;

    private final SessionService sessionService;
    private final ObjectStorageWriter objectStorageWriter;
    private final Clock clock;

    public SessionAttachmentService(
        SessionService sessionService,
        ObjectStorageWriter objectStorageWriter,
        Clock clock
    ) {
        this.sessionService = sessionService;
        this.objectStorageWriter = objectStorageWriter;
        this.clock = clock;
    }

    public UploadedFile upload(Long sessionId, Long labelerId, MultipartFile file) {
        SessionEntity session = sessionService.assertLabelerOwnsSession(sessionId, labelerId);
        if (file == null || file.isEmpty()) {
            throw new InvalidSessionAttachmentException("attachment file is required");
        }
        if (file.getSize() > MAX_ATTACHMENT_BYTES) {
            throw new InvalidSessionAttachmentException("attachment file exceeds 25MB");
        }

        String fileName = safeFileName(file.getOriginalFilename());
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
            ? "application/octet-stream"
            : file.getContentType();
        String objectKey = objectKey(session, fileName);
        try {
            objectStorageWriter.putObject(objectKey, file.getBytes(), contentType);
        } catch (IOException exception) {
            throw new InvalidSessionAttachmentException("attachment file could not be read");
        }
        return new UploadedFile()
            .objectKey(objectKey)
            .fileName(fileName)
            .contentType(contentType)
            .sizeBytes(file.getSize());
    }

    private String objectKey(SessionEntity session, String fileName) {
        String day = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDateTime.now(clock));
        return "session-attachments/%s/task-%d/session-%d/%s-%s".formatted(
            day,
            session.getTaskId(),
            session.getId(),
            UUID.randomUUID(),
            fileName
        );
    }

    private String safeFileName(String original) {
        String fallback = "attachment.bin";
        String value = original == null || original.isBlank() ? fallback : original;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        String safe = normalized.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^[.-]+", "")
            .replaceAll("[.-]+$", "");
        return safe.isBlank() ? fallback : safe;
    }
}
