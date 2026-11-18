package com.iturtle.infra.objectstorage.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DownloadOptionsTest {

    @Test
    void attachmentWithKoreanName() {
        assertEquals("attachment; filename=\"___.docx\"; filename*=UTF-8''%ED%9A%8C%EC%9D%98%EB%A1%9D.docx",
                DownloadOptions.attachment("회의록.docx").contentDispositionHeader());
    }

    @Test
    void inlineAsciiNameWithSpaces() {
        DownloadOptions options = DownloadOptions.inline("final report.pdf", "application/pdf");
        assertEquals(DownloadOptions.Disposition.INLINE, options.disposition());
        assertEquals("inline; filename=\"final report.pdf\"; filename*=UTF-8''final%20report.pdf",
                options.contentDispositionHeader());
    }

    @Test
    void headerInjectionRemoved() {
        String header = DownloadOptions.attachment("a\"b\r\nSet-Cookie: x=1\\.txt").contentDispositionHeader();
        assertFalse(header.contains("\r"));
        assertFalse(header.contains("\n"));
        assertEquals("attachment; filename=\"abSet-Cookie: x=1.txt\"; filename*=UTF-8''abSet-Cookie%3A%20x%3D1.txt", header);
    }

    @Test
    void withoutName() {
        assertEquals("attachment", DownloadOptions.attachment(null).contentDispositionHeader());
        assertEquals("inline", new DownloadOptions(" ", DownloadOptions.Disposition.INLINE, null).contentDispositionHeader());
        assertEquals(DownloadOptions.Disposition.ATTACHMENT, new DownloadOptions("a", null, null).disposition());
    }

    @Test
    void completedPartValidation() {
        assertThrows(IllegalArgumentException.class, () -> new CompletedPart(0, "etag"));
        assertThrows(IllegalArgumentException.class, () -> new CompletedPart(10_001, "etag"));
        assertThrows(IllegalArgumentException.class, () -> new CompletedPart(1, " "));
        assertEquals(1, new CompletedPart(1, "\"abc\"").partNumber());
    }
}
