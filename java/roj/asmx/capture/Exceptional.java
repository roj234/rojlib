package roj.asmx.capture;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/6/30 0030 19:15
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Exceptional {}