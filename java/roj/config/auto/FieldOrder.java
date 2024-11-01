package roj.config.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 序列化哪些字段，以及它们的顺序
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface FieldOrder {
    String[] value() default "";
}