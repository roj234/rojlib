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
 * Filename: RemapClass.java
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Nixim {
    /**
     * 嘿嘿，匿名内部类你给我搞个class出来啊
     *
     * @return target class name
     */
    String value();

    boolean copyItf() default false;

    //boolean replace() default false;
}
