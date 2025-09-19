package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 这是静态函数，WIP
 * @author Roj234
 * @since 2025/9/22 18:37
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface StaticMethod {}
