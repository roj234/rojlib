package roj.compiler.plugins.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段，也作为无参函数
 * @author Roj234
 * @since 2025/4/29 9:06
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD})
public @interface AsMethod {}