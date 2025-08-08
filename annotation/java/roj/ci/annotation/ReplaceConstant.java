package roj.ci.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/2/16 2:52
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ReplaceConstant {}
