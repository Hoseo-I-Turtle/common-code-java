package com.iturtle.core.file;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FileTypePolicyTest {

    private static final byte[] PNG = bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D);
    private static final byte[] JPEG = bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F');
    private static final byte[] PDF = ascii("%PDF-1.7\n%âãÏÓ");
    private static final byte[] ZIP = bytes('P', 'K', 0x03, 0x04, 0x14, 0, 0x06, 0);
    private static final byte[] OLE2 = bytes(0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1, 0, 0);
    private static final byte[] TEXT = "이름,학번\n홍길동,20231234\n".getBytes(StandardCharsets.UTF_8);

    private final FileTypePolicy policy = FileTypePolicy.defaults();

    @Test
    void detectsCommonFormats() {
        assertEquals(FileSignature.PNG, FileSignature.match(PNG));
        assertEquals(FileSignature.JPEG, FileSignature.match(JPEG));
        assertEquals(FileSignature.GIF, FileSignature.match(ascii("GIF89a....")));
        assertEquals(FileSignature.WEBP, FileSignature.match(ascii("RIFF\0\0\0\0WEBPVP8 ")));
        assertEquals(FileSignature.PDF, FileSignature.match(PDF));
        assertEquals(FileSignature.ZIP, FileSignature.match(ZIP));
        assertEquals(FileSignature.OLE2, FileSignature.match(OLE2));
        assertEquals(FileSignature.GZIP, FileSignature.match(bytes(0x1F, 0x8B, 0x08)));
        assertEquals(FileSignature.SEVEN_ZIP, FileSignature.match(bytes('7', 'z', 0xBC, 0xAF, 0x27, 0x1C)));
        assertEquals(FileSignature.RAR, FileSignature.match(ascii("Rar!\u001A\u0007")));
        assertEquals(FileSignature.MP4, FileSignature.match(ascii("\0\0\0\u0018ftypmp42")));
        assertEquals(FileSignature.ELF, FileSignature.match(bytes(0x7F, 'E', 'L', 'F', 2, 1)));
        assertEquals(FileSignature.SCRIPT, FileSignature.match(ascii("#!/bin/sh\nrm -rf /")));
        assertEquals(FileSignature.SVG, FileSignature.match(ascii("<?xml version=\"1.0\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\">")));
        assertEquals(FileSignature.SVG, FileSignature.match(utf8("\uFEFF  <svg width=\"1\">")));
        assertNull(FileSignature.match(utf8("\uC774 \uBB38\uC11C\uB294 <svg> \uD0DC\uADF8 \uC0AC\uC6A9\uBC95\uC744 \uC124\uBA85\uD569\uB2C8\uB2E4")));
        assertNull(FileSignature.match(TEXT));
        assertNull(FileSignature.match(new byte[0]));
        assertNull(FileSignature.match(null));
    }

    @Test
    void windowsExecutableNeedsPeHeader() {
        byte[] exe = new byte[0x100];
        exe[0] = 'M';
        exe[1] = 'Z';
        exe[0x3C] = (byte) 0x80;   // PE 헤더 위치
        exe[0x80] = 'P';
        exe[0x81] = 'E';
        assertEquals(FileSignature.WINDOWS_EXECUTABLE, FileSignature.match(exe));

        // "MZ" 로 시작하는 텍스트는 실행 파일이 아니다 (0x3C 자리가 글자라 PE 위치가 터무니없음)
        assertNull(FileSignature.match(utf8("MZ project notes: schedule, owners, risks and the next steps for the team.")));
        // 64바이트보다 짧으면 판단하지 않는다
        assertNull(FileSignature.match(ascii("MZ")));
    }

    @Test
    void bmpNeedsDibHeader() {
        byte[] bmp = new byte[32];
        bmp[0] = 'B';
        bmp[1] = 'M';
        bmp[14] = 40;
        assertEquals(FileSignature.BMP, FileSignature.match(bmp));
        assertNull(FileSignature.match(utf8("BMW 2026 견적서입니다.")));
    }

    @Test
    void allowsMatchingExtension() {
        FileTypeCheck png = policy.check("사진.PNG", PNG);
        assertTrue(png.allowed());
        assertEquals(FileTypeCheck.Reason.OK, png.reason());
        assertEquals("png", png.extension());
        assertEquals("image/png", png.contentType());
        assertTrue(png.inlineViewable());

        assertTrue(policy.check("회의록.docx", ZIP).allowed());
        assertTrue(policy.check("보고서.hwp", OLE2).allowed());
        assertTrue(policy.check("자료.zip", ZIP).allowed());
        assertTrue(policy.check("논문.pdf", PDF).inlineViewable());
        assertFalse(policy.check("자료.zip", ZIP).inlineViewable());
    }

    @Test
    void allowsUnknownTextAsDownload() {
        FileTypeCheck csv = policy.check("명단.csv", TEXT);
        assertTrue(csv.allowed());
        assertNull(csv.detected());
        assertEquals("application/octet-stream", csv.contentType());
        assertFalse(csv.inlineViewable());
        assertTrue(policy.check("README", TEXT).allowed());   // 확장자 없음
        assertTrue(policy.check("빈파일.txt", new byte[0]).allowed());
    }

    @Test
    void svgIsAllowedButNeverInline() {
        FileTypeCheck svg = policy.check("logo.svg", ascii("<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"));
        assertTrue(svg.allowed());
        assertEquals("image/svg+xml", svg.contentType());
        assertFalse(svg.inlineViewable());
    }

    @Test
    void rejectsBlockedExtension() {
        assertEquals(FileTypeCheck.Reason.BLOCKED_EXTENSION, policy.check("report.pdf.exe", PDF).reason());
        assertEquals(FileTypeCheck.Reason.BLOCKED_EXTENSION, policy.check("run.BAT", TEXT).reason());
        assertEquals(FileTypeCheck.Reason.BLOCKED_EXTENSION, policy.check("C:\\temp\\app.js", TEXT).reason());
        assertEquals(FileTypeCheck.Reason.BLOCKED_EXTENSION, policy.check("lib.jar", ZIP).reason());
    }

    @Test
    void rejectsExecutableContentWhateverTheExtension() {
        byte[] elf = bytes(0x7F, 'E', 'L', 'F', 2, 1, 1);
        assertEquals(FileTypeCheck.Reason.EXECUTABLE_CONTENT, policy.check("image.png", elf).reason());
        assertEquals(FileTypeCheck.Reason.EXECUTABLE_CONTENT, policy.check("notes.txt", ascii("#!/usr/bin/env python")).reason());
    }

    @Test
    void rejectsExtensionMismatch() {
        // 확장자는 png 인데 내용은 JPEG
        FileTypeCheck renamed = policy.check("a.png", JPEG);
        assertFalse(renamed.allowed());
        assertEquals(FileTypeCheck.Reason.EXTENSION_MISMATCH, renamed.reason());
        assertEquals(FileSignature.JPEG, renamed.detected());
        // 확장자는 pdf 인데 내용을 모름 (텍스트)
        assertEquals(FileTypeCheck.Reason.EXTENSION_MISMATCH, policy.check("fake.pdf", TEXT).reason());
        // 확장자는 txt 인데 내용은 PNG
        assertEquals(FileTypeCheck.Reason.EXTENSION_MISMATCH, policy.check("a.txt", PNG).reason());
    }

    @Test
    void customBlockedExtensions() {
        FileTypePolicy custom = FileTypePolicy.of(Set.of(".ZIP", " exe "));
        assertEquals(Set.of("zip", "exe"), custom.blockedExtensions());
        assertEquals(FileTypeCheck.Reason.BLOCKED_EXTENSION, custom.check("a.zip", ZIP).reason());
        assertTrue(custom.check("run.bat", TEXT).allowed());   // 기본 목록을 대신한다
    }

    @Test
    void extensionOf() {
        assertEquals("png", FileTypePolicy.extensionOf("a.b.PNG"));
        assertEquals("", FileTypePolicy.extensionOf(".gitignore"));
        assertEquals("", FileTypePolicy.extensionOf("trailing."));
        assertEquals("", FileTypePolicy.extensionOf("noext"));
        assertEquals("", FileTypePolicy.extensionOf(null));
        assertEquals("txt", FileTypePolicy.extensionOf("dir.v2/readme.txt"));
        assertEquals("", FileTypePolicy.extensionOf("dir.v2/readme"));
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
