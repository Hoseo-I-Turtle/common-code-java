package com.iturtle.infra.objectstorage.dto;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 내려받기 URL 로 받을 때 응답 헤더를 정한다 (서명 주소의 {@code response-content-disposition} · {@code response-content-type}).
 * 스토리지 객체 키에는 원래 파일 이름을 넣지 않으므로, 사용자가 받을 이름은 여기서 정한다.
 * <pre>
 * storage.createDownloadUrl(key, Duration.ofMinutes(5), DownloadOptions.attachment("회의록.docx"));
 * storage.createDownloadUrl(key, Duration.ofMinutes(5), DownloadOptions.inline("논문.pdf", "application/pdf"));
 * </pre>
 *
 * @param filename    받을 파일 이름. null 이면 이름을 정하지 않는다
 * @param disposition 브라우저에서 열지({@code INLINE}) 내려받을지({@code ATTACHMENT})
 * @param contentType 응답 Content-Type. null 이면 객체에 저장된 값
 */
public record DownloadOptions(String filename, Disposition disposition, String contentType) {

    public enum Disposition {
        /** 브라우저에서 연다 (이미지 · PDF 미리 보기). 스크립트를 담을 수 있는 형식(HTML, SVG)에는 쓰지 않는다 */
        INLINE,
        /** 항상 내려받는다 */
        ATTACHMENT
    }

    public DownloadOptions {
        if (disposition == null) {
            disposition = Disposition.ATTACHMENT;
        }
    }

    public static DownloadOptions attachment(String filename) {
        return new DownloadOptions(filename, Disposition.ATTACHMENT, null);
    }

    public static DownloadOptions inline(String filename, String contentType) {
        return new DownloadOptions(filename, Disposition.INLINE, contentType);
    }

    /**
     * {@code Content-Disposition} 값 (RFC 6266). 한글 등은 {@code filename*} 에 UTF-8 로 넣고,
     * 옛 브라우저용 {@code filename} 에는 ASCII 만 남긴다. 따옴표 · 역슬래시 · 줄바꿈은 지운다 (헤더 주입 방지).
     */
    public String contentDispositionHeader() {
        String type = disposition == Disposition.INLINE ? "inline" : "attachment";
        if (filename == null || filename.isBlank()) {
            return type;
        }
        String cleaned = filename.replaceAll("[\\r\\n\"\\\\]", "").strip();
        String asciiFallback = cleaned.replaceAll("[^\\x20-\\x7E]", "_");
        String encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8).replace("+", "%20");
        return type + "; filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }
}
