package com.iturtle.spring.web.json;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.ReferenceTypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.module.SimpleDeserializers;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.type.ReferenceType;
import java.util.Optional;

/**
 * PATCH 요청 본문에서 "보내지 않은 칸"과 "null 을 보낸 칸"을 구분하게 하는 Jackson 2 (Spring Boot 3) 모듈.
 * <p>
 * 요청 DTO(record)의 칸을 {@code Optional<T>} 로 두면 다음과 같이 들어온다.
 * <ul>
 *     <li>보내지 않음 → {@code null} (그대로 둔다)</li>
 *     <li>{@code null} 을 보냄 → {@code Optional.empty()} (비운다)</li>
 *     <li>값을 보냄 → {@code Optional.of(값)} (바꾼다)</li>
 * </ul>
 * Jackson 기본 동작은 보내지 않은 {@code Optional} 칸도 {@code Optional.empty()} 로 채워 둘을 구분할 수 없다.
 * 이 모듈은 {@code Optional} 의 "없음" 값만 바꾸며, null · 값을 읽는 방법은 Jackson 기본과 같다.
 * <pre>
 * public record NoticeUpdateRequest(Optional&lt;String&gt; title, Optional&lt;LocalDate&gt; pinnedUntilOn) {}
 *
 * if (request.pinnedUntilOn() != null) {              // 보낸 칸만
 *     notice.pin(request.pinnedUntilOn().orElse(null)); // null 을 보냈으면 고정 해제
 * }
 * </pre>
 * common-webmvc-starter 가 빈으로 등록하고 Spring Boot 가 JSON 매퍼에 넣는다. {@code iturtle.web.json.absent-optional-as-null=false} 로 끈다.
 */
public class PatchOptionalJackson2Module extends SimpleModule {

    public static final String NAME = "iturtle-patch-optional";

    public PatchOptionalJackson2Module() {
        super(NAME);
        setDeserializers(new SimpleDeserializers() {
            @Override
            public JsonDeserializer<?> findReferenceDeserializer(ReferenceType type, DeserializationConfig config,
                                                                 BeanDescription beanDesc,
                                                                 TypeDeserializer contentTypeDeserializer,
                                                                 JsonDeserializer<?> contentDeserializer) {
                return type.hasRawClass(Optional.class)
                        ? new OptionalDeserializer(type, null, contentTypeDeserializer, contentDeserializer)
                        : null;
            }

            @Override
            public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
                return valueType == Optional.class;
            }
        });
    }

    /**
     * 모듈 이름이 같으면 Jackson 이 두 번 등록하지 않는다.
     */
    @Override
    public Object getTypeId() {
        return NAME;
    }

    static final class OptionalDeserializer extends ReferenceTypeDeserializer<Optional<?>> {

        OptionalDeserializer(JavaType fullType, ValueInstantiator instantiator, TypeDeserializer typeDeserializer,
                             JsonDeserializer<?> deserializer) {
            super(fullType, instantiator, typeDeserializer, deserializer);
        }

        @Override
        protected OptionalDeserializer withResolved(TypeDeserializer typeDeserializer, JsonDeserializer<?> valueDeserializer) {
            return new OptionalDeserializer(_fullType, _valueInstantiator, typeDeserializer, valueDeserializer);
        }

        /** 보내지 않은 칸 */
        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return null;
        }

        /** null 을 보낸 칸 */
        @Override
        public Optional<?> getNullValue(DeserializationContext ctxt) {
            return Optional.empty();
        }

        @Override
        public Object getEmptyValue(DeserializationContext ctxt) {
            return Optional.empty();
        }

        @Override
        public Optional<?> referenceValue(Object contents) {
            return Optional.ofNullable(contents);
        }

        @Override
        public Optional<?> updateReference(Optional<?> reference, Object contents) {
            return Optional.ofNullable(contents);
        }

        @Override
        public Object getReferenced(Optional<?> reference) {
            return reference.orElse(null);
        }
    }
}
