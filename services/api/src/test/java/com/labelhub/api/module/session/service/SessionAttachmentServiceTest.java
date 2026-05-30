package com.labelhub.api.module.session.service;

import com.labelhub.api.module.export.storage.ObjectStorageWriter;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.exception.InvalidSessionAttachmentException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionAttachmentServiceTest {

    private final SessionService sessionService = mock(SessionService.class);
    private final ObjectStorageWriter objectStorageWriter = mock(ObjectStorageWriter.class);
    private final SessionAttachmentService service = new SessionAttachmentService(
        sessionService,
        objectStorageWriter,
        Clock.fixed(Instant.parse("2026-05-30T12:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void uploadStoresFileUnderLabelerOwnedSessionPrefix() {
        SessionEntity session = new SessionEntity();
        session.setId(55L);
        session.setTaskId(44L);
        when(sessionService.assertLabelerOwnsSession(55L, 2002L)).thenReturn(session);
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "../Report Final.PNG",
            "image/png",
            "payload".getBytes(StandardCharsets.UTF_8)
        );

        var uploaded = service.upload(55L, 2002L, file);

        assertThat(uploaded.getFileName()).isEqualTo("report-final.png");
        assertThat(uploaded.getContentType()).isEqualTo("image/png");
        assertThat(uploaded.getSizeBytes()).isEqualTo(7);
        assertThat(uploaded.getObjectKey())
            .startsWith("session-attachments/20260530/task-44/session-55/")
            .endsWith("-report-final.png");
        ArgumentCaptor<byte[]> contentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(objectStorageWriter).putObject(eq(uploaded.getObjectKey()), contentCaptor.capture(), eq("image/png"));
        assertThat(contentCaptor.getValue()).isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void uploadRejectsEmptyFile() {
        SessionEntity session = new SessionEntity();
        session.setId(55L);
        session.setTaskId(44L);
        when(sessionService.assertLabelerOwnsSession(55L, 2002L)).thenReturn(session);

        assertThatThrownBy(() -> service.upload(55L, 2002L, new MockMultipartFile("file", new byte[0])))
            .isInstanceOf(InvalidSessionAttachmentException.class)
            .hasMessageContaining("required");
    }
}
