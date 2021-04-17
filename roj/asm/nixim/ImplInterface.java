package roj.asm.nixim;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/19 18:04
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ImplInterface {
    Class<?>[] value();
}
