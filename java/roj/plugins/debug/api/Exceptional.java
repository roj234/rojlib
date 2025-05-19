package roj.plugins.debug.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/3/23 15:44
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface Exceptional {}
