package roj.config.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 设置字段的序列化属性
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD,ElementType.TYPE})
public @interface Optional {
    Mode value() default Mode.IF_DEFAULT;
    enum Mode {
        /**
         * 这个字段总会被序列化
         */
        NEVER,
        /**
         * 这个字段在非默认值(0, false, null)时会被序列化
         */
        IF_DEFAULT,
        /**
         * 这个字段在非空时会被序列化，仅适用于对象类型.
         * 在基本类型上使用和IF_DEFAULT效果相同.
         * 会额外调用一些简单的isEmpty函数判断.
         * @deprecated 建议使用writeIgnore (WIP)
         */
        IF_EMPTY
    }

    /**
     * 序列化忽略类型.
     * 默认/NEVER => 遵从value的设置
     * ALWAYS => 这个值不会被序列化 (但是能被反序列化)
     * 其它 => 使用自定义Predicate (未实现)
     */
    String writeIgnore() default "NEVER";
    /**
     * 反序列化忽略.
     * 为true时，该值不会被反序列化
     */
    boolean readIgnore() default false;
}