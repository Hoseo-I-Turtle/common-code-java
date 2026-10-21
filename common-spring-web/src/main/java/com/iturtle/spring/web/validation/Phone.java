package com.iturtle.spring.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 국내 전화번호 형식. 하이픈은 있어도 없어도 된다. null 은 통과하므로 필수 값이면 @NotBlank 를 함께 붙인다.
 * <ul>
 *     <li>MOBILE: 휴대전화 (010 / 011 / 016 / 017 / 018 / 019)</li>
 *     <li>ANY(기본): 휴대전화 + 유선(02, 지역번호) + 인터넷전화(070) + 개인번호(050X) + 대표번호(15XX / 16XX / 18XX)</li>
 * </ul>
 * <pre>
 * &#64;Phone(type = Phone.Type.MOBILE)
 * private String mobile;
 * </pre>
 */
@Documented
@Constraint(validatedBy = PhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Phone {

    Type type() default Type.ANY;

    String message() default "올바른 전화번호 형식이 아닙니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    enum Type {
        MOBILE,
        ANY
    }
}
