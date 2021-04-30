package roj.asm.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: Shadow.java
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface Shadow {
    /**
     * @return target field name
     */
    String value();

    boolean direction() default false;

    // todo
    boolean MAP_SELF_TO_VALUE = false;
    boolean MAP_VALUE_TO_SELF = true;
}
