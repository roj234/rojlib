package roj.config.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 读取时是可选的
 * 为null (对于基本类型是0或false)时不写入
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD,ElementType.TYPE})
public @interface Optional {
    Mode value() default Mode.IF_DEFAULT;
    enum Mode {NEVER, IF_DEFAULT, IF_NULL, IF_EMPTY}

    // WIP
    //String predicate() default "";
}