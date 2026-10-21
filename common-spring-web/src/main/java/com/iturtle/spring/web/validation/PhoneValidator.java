package com.iturtle.spring.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PhoneValidator implements ConstraintValidator<Phone, String> {

    private static final Pattern MOBILE = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");

    private static final Pattern ANY = Pattern.compile(String.join("|",
            "^01[016789]-?\\d{3,4}-?\\d{4}$",                     // 휴대전화
            "^02-?\\d{3,4}-?\\d{4}$",                             // 서울
            "^0(3[1-3]|4[1-4]|5[1-5]|6[1-4])-?\\d{3,4}-?\\d{4}$", // 지역번호
            "^070-?\\d{4}-?\\d{4}$",                              // 인터넷전화
            "^050\\d-?\\d{3,4}-?\\d{4}$",                         // 개인번호
            "^1[568]\\d{2}-?\\d{4}$"                              // 대표번호
    ));

    private Pattern pattern;

    @Override
    public void initialize(Phone annotation) {
        this.pattern = annotation.type() == Phone.Type.MOBILE ? MOBILE : ANY;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || pattern.matcher(value).matches();
    }
}
