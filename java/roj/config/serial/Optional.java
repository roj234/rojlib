package roj.config.serial;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 读取时是可选的
 * 为null时不写入
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD,ElementType.TYPE})
public @interface Optional {
    boolean value() default true;
}
