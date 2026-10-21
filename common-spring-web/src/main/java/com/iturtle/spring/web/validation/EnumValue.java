package com.iturtle.spring.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 문자열이 enum 상수 이름 중 하나인지 검증한다. 요청 값을 enum 으로 바로 받으면 잘못된 값이 400 C001(본문 파싱 실패)로만
 * 응답되므로, 문자열로 받아 어떤 필드가 틀렸는지 알려줄 때 사용한다.
 * <pre>
 * &#64;EnumValue(enumClass = OrderStatus.class)
 * private String status;
 * </pre>
 * null 은 통과하므로 필수 값이면 @NotBlank 를 함께 붙인다.
 */
@Documented
@Constraint(validatedBy = EnumValueValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface EnumValue {

    Class<? extends Enum<?>> enumClass();

    boolean ignoreCase() default false;

    String message() default "허용되지 않는 값입니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
