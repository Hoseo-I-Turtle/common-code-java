package com.iturtle.core.file;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 올린 파일의 이름과 앞부분으로 받아도 되는지 판단한다. 확장자만 믿지 않고 실제 내용(매직 넘버)과 대조한다.
 * <ol>
 *   <li>마지막 확장자가 차단 목록에 있으면 거절한다 ({@code report.pdf.exe} → exe).</li>
 *   <li>내용이 실행 파일 · 스크립트면 확장자와 상관없이 거절한다 ({@code setup.png} 인데 내용이 exe).</li>
 *   <li>내용의 형식을 알면, 확장자가 그 형식의 확장자가 아니면 거절한다 ({@code a.png} 인데 내용이 JPEG).</li>
 *   <li>내용의 형식을 모르면(텍스트 · CSV 등), 시그니처가 있어야 하는 확장자({@code png}, {@code pdf}, {@code docx} 등)면 거절한다.</li>
 * </ol>
 * 압축 파일은 받는다 (안의 파일은 검사하지 않는다). 크기 제한, 쓰임(프로필 사진은 이미지만 등)은 서비스가 따로 검사한다.
 * <pre>
 * FileTypePolicy policy = FileTypePolicy.defaults();          // 또는 FileTypePolicy.of(공통코드의 차단 확장자)
 * FileTypeCheck check = policy.check("회의록.docx", headBytes); // headBytes = 파일 앞 FileTypePolicy.HEAD_BYTES 바이트
 * if (!check.allowed()) throw new BusinessException(...);
 * String contentType = check.contentType();                   // 모르는 형식이면 application/octet-stream
 * </pre>
 *
 * @param blockedExtensions 막을 확장자 (점 없이, 소문자로 바꿔 비교)
 */
public record FileTypePolicy(Set<String> blockedExtensions) {

    /**
     * 판단에 필요한 앞부분 길이. 이보다 짧게 줘도 되지만 SVG 처럼 텍스트로 찾는 형식은 놓칠 수 있다.
     */
    public static final int HEAD_BYTES = 512;

    /**
     * 기본 차단 확장자: 실행 파일, 스크립트, 설치 파일, 바로 가기.
     */
    public static final Set<String> DEFAULT_BLOCKED_EXTENSIONS = Set.of(
            "exe", "dll", "sys", "scr", "com", "cpl", "msi", "msp", "bat", "cmd", "ps1", "psm1", "vbs", "vbe",
            "js", "jse", "mjs", "wsf", "wsh", "hta", "jar", "apk", "app", "dmg", "pkg", "deb", "rpm", "sh", "bash",
            "zsh", "so", "dylib", "lnk", "reg", "class");

    /**
     * 내용의 시그니처가 있어야 하는 확장자 (이미지 · 문서 · 압축). 이 확장자인데 시그니처를 모르면 거절한다.
     * 미디어(옛 mov 등 시그니처가 일정하지 않음)와 SVG(텍스트), 실행 파일(차단 목록으로 막음)은 넣지 않는다.
     */
    private static final Set<String> SIGNATURE_REQUIRED = java.util.Arrays.stream(FileSignature.values())
            .filter(signature -> signature.kind() == FileSignature.Kind.IMAGE
                    || signature.kind() == FileSignature.Kind.DOCUMENT
                    || signature.kind() == FileSignature.Kind.ARCHIVE)
            .flatMap(signature -> signature.extensions().stream())
            .collect(Collectors.toUnmodifiableSet());

    public FileTypePolicy {
        blockedExtensions = blockedExtensions.stream()
                .map(extension -> normalize(extension))
                .filter(extension -> !extension.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public static FileTypePolicy defaults() {
        return new FileTypePolicy(DEFAULT_BLOCKED_EXTENSIONS);
    }

    /**
     * 차단 확장자를 직접 정한다 (공통코드 등). 기본 목록을 대신하므로, 더하려면 {@link #DEFAULT_BLOCKED_EXTENSIONS} 와 합친다.
     */
    public static FileTypePolicy of(Set<String> blockedExtensions) {
        return new FileTypePolicy(blockedExtensions);
    }

    /**
     * 파일을 받아도 되는지 판단한다.
     *
     * @param filename 올린 사람이 붙인 이름
     * @param head     파일 앞부분 ({@link #HEAD_BYTES} 바이트 권장). 빈 파일이면 빈 배열
     */
    public FileTypeCheck check(String filename, byte[] head) {
        String extension = extensionOf(filename);
        FileSignature detected = FileSignature.match(head);
        if (!extension.isEmpty() && blockedExtensions.contains(extension)) {
            return FileTypeCheck.rejected(FileTypeCheck.Reason.BLOCKED_EXTENSION, extension, detected);
        }
        if (detected != null && detected.isExecutable()) {
            return FileTypeCheck.rejected(FileTypeCheck.Reason.EXECUTABLE_CONTENT, extension, detected);
        }
        if (detected != null && !detected.allowsExtension(extension)) {
            return FileTypeCheck.rejected(FileTypeCheck.Reason.EXTENSION_MISMATCH, extension, detected);
        }
        if (detected == null && SIGNATURE_REQUIRED.contains(extension)) {
            return FileTypeCheck.rejected(FileTypeCheck.Reason.EXTENSION_MISMATCH, extension, null);
        }
        return FileTypeCheck.allowed(extension, detected);
    }

    /**
     * 마지막 점 뒤의 확장자 (소문자). 없으면 "". 경로가 섞여 와도 파일 이름 부분만 본다.
     */
    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        String name = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1).strip();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return normalize(name.substring(dot + 1));
    }

    private static String normalize(String extension) {
        String value = extension.strip().toLowerCase(Locale.ROOT);
        return value.startsWith(".") ? value.substring(1) : value;
    }
}
