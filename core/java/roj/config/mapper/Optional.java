package roj.config.mapper;

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
    /**
     * 字段序列化模式
     * 注解默认值：非空时序列化
     * 无注解的默认值：始终
     */
    WriteMode write() default WriteMode.NON_NULL;
    enum WriteMode {
        /**
         * 始终跳过该字段（不参与序列化）
         */
        SKIP,
        /**
         * 始终序列化该字段（无论字段值如何）
         */
        ALWAYS,
        /**
         * 仅当字段值非空时序列化（适用规则：<br>
         * - 原始类型：非默认值（如非0/false）<br>
         * - 对象类型：非null
         */
        NON_NULL,
        /**
         * 仅当字段值非空且非空集合/字符串时序列化<br>
         * (原始类型处理同{@link #NON_NULL})<br>
         * 即将弃用 使用 {@link #CUSTOM} 替代
         */
        NON_BLANK,
        CUSTOM
    }

    String nullValue() default "";

    /**
     * 字段反序列化模式
     * 注解默认值：可选
     * 无注解的默认值：REQUIRED
     */
    ReadMode read() default ReadMode.OPTIONAL;
    enum ReadMode {
        /**
         * 必须存在字段值（不存在则抛出异常）
         */
        REQUIRED,
        /**
         * 字段值可选（不存在时设为默认值）
         */
        OPTIONAL,
        /**
         * 完全跳过该字段（不参与反序列化）
         */
        IGNORED
    }
}