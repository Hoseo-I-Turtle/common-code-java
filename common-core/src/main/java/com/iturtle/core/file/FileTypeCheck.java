package com.iturtle.core.file;

/**
 * {@link FileTypePolicy#check} 의 결과.
 *
 * @param allowed   받아도 되는지
 * @param reason    거절했으면 그 이유, 받으면 {@link Reason#OK}
 * @param extension 마지막 확장자 (소문자, 없으면 "")
 * @param detected  내용으로 알아낸 형식. 모르면 null
 */
public record FileTypeCheck(boolean allowed, Reason reason, String extension, FileSignature detected) {

    public static final String UNKNOWN_CONTENT_TYPE = "application/octet-stream";

    public enum Reason {
        OK,
        /** 차단한 확장자 */
        BLOCKED_EXTENSION,
        /** 내용이 실행 파일 · 스크립트 */
        EXECUTABLE_CONTENT,
        /** 확장자와 내용 형식이 다름 */
        EXTENSION_MISMATCH
    }

    static FileTypeCheck allowed(String extension, FileSignature detected) {
        return new FileTypeCheck(true, Reason.OK, extension, detected);
    }

    static FileTypeCheck rejected(Reason reason, String extension, FileSignature detected) {
        return new FileTypeCheck(false, reason, extension, detected);
    }

    /**
     * 저장 · 응답에 쓸 MIME. 내용으로 알아낸 형식이 있으면 그것, 없으면 {@code application/octet-stream}.
     * 올린 사람이 보낸 Content-Type 은 믿지 않는다.
     */
    public String contentType() {
        return detected == null ? UNKNOWN_CONTENT_TYPE : detected.mimeType();
    }

    /**
     * 브라우저에서 바로 열어도 되는지 (이미지 · PDF). 나머지는 내려받게 한다 ({@code Content-Disposition: attachment}).
     * SVG 는 스크립트를 담을 수 있어 열지 않는다.
     */
    public boolean inlineViewable() {
        return detected != null
                && (detected.kind() == FileSignature.Kind.IMAGE || detected == FileSignature.PDF);
    }
}
