/**
 * This file is a part of MI <br>
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: OpenAny.java
 */
package roj.asm.annotation;

import java.lang.annotation.*;

@Repeatable(OpenAnys.class)
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.TYPE)
public @interface OpenAny {
    String[] names() default {"*"};

    String value();

    boolean compileOnly() default false;
}