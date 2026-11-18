package com.iturtle.core.file;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 파일 앞부분(매직 넘버)으로 알 수 있는 형식.
 * <p>
 * 같은 바탕 형식을 쓰는 파일은 한 항목으로 묶는다. 예: docx · xlsx · pptx · hwpx 는 ZIP, doc · xls · ppt · hwp 는 OLE2.
 * {@link #extensions()} 는 그 형식으로 올 수 있는 확장자다 (확장자 대조에 씀).
 */
public enum FileSignature {

    JPEG(Kind.IMAGE, "image/jpeg", Set.of("jpg", "jpeg", "jfif")),
    PNG(Kind.IMAGE, "image/png", Set.of("png")),
    GIF(Kind.IMAGE, "image/gif", Set.of("gif")),
    WEBP(Kind.IMAGE, "image/webp", Set.of("webp")),
    BMP(Kind.IMAGE, "image/bmp", Set.of("bmp")),
    /**
     * SVG 는 스크립트를 담을 수 있어 이미지로 보여 주면 안 된다. 그래서 IMAGE 가 아니라 {@link Kind#MARKUP} 이다.
     */
    SVG(Kind.MARKUP, "image/svg+xml", Set.of("svg")),
    PDF(Kind.DOCUMENT, "application/pdf", Set.of("pdf")),
    /**
     * ZIP 과 ZIP 을 바탕으로 한 형식 (오피스 Open XML, 한글 hwpx, OpenDocument, epub, jar, apk).
     */
    ZIP(Kind.ARCHIVE, "application/zip", Set.of("zip", "docx", "xlsx", "pptx", "hwpx", "odt", "ods", "odp", "epub", "jar", "apk")),
    /**
     * 옛 오피스 · 한글 문서가 쓰는 OLE2 복합 문서 (doc, xls, ppt, hwp, msg). msi 도 이 형식이다.
     */
    OLE2(Kind.DOCUMENT, "application/x-ole-storage", Set.of("doc", "xls", "ppt", "hwp", "msg", "msi")),
    GZIP(Kind.ARCHIVE, "application/gzip", Set.of("gz", "tgz")),
    SEVEN_ZIP(Kind.ARCHIVE, "application/x-7z-compressed", Set.of("7z")),
    RAR(Kind.ARCHIVE, "application/vnd.rar", Set.of("rar")),
    /**
     * ISO base media (mp4, mov, m4a, heic 등). 상자 종류로 세분하지 않는다.
     */
    MP4(Kind.MEDIA, "video/mp4", Set.of("mp4", "m4v", "m4a", "mov", "heic", "heif", "3gp")),
    MP3(Kind.MEDIA, "audio/mpeg", Set.of("mp3")),
    WINDOWS_EXECUTABLE(Kind.EXECUTABLE, "application/vnd.microsoft.portable-executable", Set.of("exe", "dll", "sys", "scr", "com", "cpl")),
    ELF(Kind.EXECUTABLE, "application/x-elf", Set.of("so", "bin", "elf")),
    MACH_O(Kind.EXECUTABLE, "application/x-mach-binary", Set.of("dylib", "bundle")),
    /**
     * {@code CAFEBABE}: Java class 와 Mach-O fat binary 가 같은 시작을 쓴다. 둘 다 실행 파일로 본다.
     */
    JAVA_CLASS(Kind.EXECUTABLE, "application/java-vm", Set.of("class")),
    /**
     * {@code #!} 로 시작하는 스크립트 (sh, py 등).
     */
    SCRIPT(Kind.EXECUTABLE, "text/x-script", Set.of("sh", "bash", "zsh", "py", "pl", "rb"));

    /**
     * 형식의 큰 분류. 화면에서 열지 · 내려받게 할지는 서비스가 이것으로 정한다.
     */
    public enum Kind {
        IMAGE, MARKUP, DOCUMENT, ARCHIVE, MEDIA, EXECUTABLE
    }

    private final Kind kind;
    private final String mimeType;
    private final Set<String> extensions;

    FileSignature(Kind kind, String mimeType, Set<String> extensions) {
        this.kind = kind;
        this.mimeType = mimeType;
        this.extensions = extensions;
    }

    public Kind kind() {
        return kind;
    }

    public String mimeType() {
        return mimeType;
    }

    public Set<String> extensions() {
        return extensions;
    }

    public boolean isExecutable() {
        return kind == Kind.EXECUTABLE;
    }

    /**
     * 확장자(점 없이, 대소문자 무시)가 이 형식으로 올 수 있는지.
     */
    public boolean allowsExtension(String extension) {
        return extension != null && extensions.contains(extension.toLowerCase(Locale.ROOT));
    }

    /**
     * 파일 앞부분으로 형식을 찾는다. 모르는 형식(텍스트, CSV, 알 수 없는 바이너리)은 null.
     */
    static FileSignature match(byte[] head) {
        if (head == null || head.length == 0) {
            return null;
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return JPEG;
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) return PNG;
        if (startsWithAscii(head, 0, "GIF87a") || startsWithAscii(head, 0, "GIF89a")) return GIF;
        if (startsWithAscii(head, 0, "RIFF") && startsWithAscii(head, 8, "WEBP")) return WEBP;
        if (looksLikeBmp(head)) return BMP;
        if (startsWithAscii(head, 0, "%PDF-")) return PDF;
        if (startsWith(head, 'P', 'K', 0x03, 0x04) || startsWith(head, 'P', 'K', 0x05, 0x06)
                || startsWith(head, 'P', 'K', 0x07, 0x08)) return ZIP;
        if (startsWith(head, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1)) return OLE2;
        if (startsWith(head, 0x1F, 0x8B)) return GZIP;
        if (startsWith(head, '7', 'z', 0xBC, 0xAF, 0x27, 0x1C)) return SEVEN_ZIP;
        if (startsWithAscii(head, 0, "Rar!")) return RAR;
        if (startsWithAscii(head, 4, "ftyp")) return MP4;
        if (startsWithAscii(head, 0, "ID3") || startsWith(head, 0xFF, 0xFB) || startsWith(head, 0xFF, 0xF3)
                || startsWith(head, 0xFF, 0xF2)) return MP3;
        if (looksLikeWindowsExecutable(head)) return WINDOWS_EXECUTABLE;
        if (startsWith(head, 0x7F, 'E', 'L', 'F')) return ELF;
        if (startsWith(head, 0xFE, 0xED, 0xFA, 0xCE) || startsWith(head, 0xFE, 0xED, 0xFA, 0xCF)
                || startsWith(head, 0xCE, 0xFA, 0xED, 0xFE) || startsWith(head, 0xCF, 0xFA, 0xED, 0xFE)) return MACH_O;
        if (startsWith(head, 0xCA, 0xFE, 0xBA, 0xBE)) return JAVA_CLASS;
        if (startsWithAscii(head, 0, "#!")) return SCRIPT;
        if (looksLikeSvg(head)) return SVG;
        return null;
    }

    private static boolean startsWith(byte[] head, int... bytes) {
        if (head.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if ((head[i] & 0xFF) != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] head, int offset, String ascii) {
        byte[] expected = ascii.getBytes(StandardCharsets.US_ASCII);
        if (head.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (head[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * "BM" 은 텍스트도 흔히 시작하는 글자라, 뒤의 DIB 헤더 크기(12 · 40 · 52 · 56 · 108 · 124)까지 맞아야 BMP 로 본다.
     */
    private static boolean looksLikeBmp(byte[] head) {
        if (!startsWithAscii(head, 0, "BM") || head.length < 18) {
            return false;
        }
        int dibHeaderSize = littleEndianInt(head, 14);
        return dibHeaderSize == 12 || dibHeaderSize == 40 || dibHeaderSize == 52 || dibHeaderSize == 56
                || dibHeaderSize == 108 || dibHeaderSize == 124;
    }

    /**
     * "MZ" 로 시작하고, 0x3C 의 PE 헤더 위치가 앞부분 안이면 거기에 "PE\0\0" 이 있어야 한다.
     * 위치가 앞부분 밖이면 위치 값이 그럴듯할 때(64KB 미만)만 실행 파일로 본다.
     * "MZ" 로 시작하는 텍스트는 그 자리가 글자라 위치 값이 매우 커서 걸리지 않는다.
     */
    private static boolean looksLikeWindowsExecutable(byte[] head) {
        if (!startsWithAscii(head, 0, "MZ") || head.length < 0x40) {
            return false;
        }
        long peOffset = littleEndianInt(head, 0x3C) & 0xFFFFFFFFL;
        if (peOffset + 4 <= head.length) {
            return startsWith(java.util.Arrays.copyOfRange(head, (int) peOffset, (int) peOffset + 4), 'P', 'E', 0, 0);
        }
        return peOffset < 0x10000;
    }

    private static int littleEndianInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | (bytes[offset + 1] & 0xFF) << 8 | (bytes[offset + 2] & 0xFF) << 16
                | (bytes[offset + 3] & 0xFF) << 24;
    }

    /**
     * 앞부분이 텍스트이고 (BOM · 공백 · XML 선언 · 주석 뒤에) {@code <svg} 가 나오면 SVG 로 본다.
     */
    private static boolean looksLikeSvg(byte[] head) {
        String text = new String(head, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        int svg = text.indexOf("<svg");
        if (svg < 0) {
            return false;
        }
        String before = text.substring(0, svg).replace("﻿", "").trim();
        return before.isEmpty() || before.startsWith("<?xml") || before.startsWith("<!--") || before.startsWith("<!doctype");
    }
}
