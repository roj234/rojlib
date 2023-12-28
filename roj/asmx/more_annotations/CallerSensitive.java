package roj.asmx.more_annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/1/6 0006 4:15
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface CallerSensitive {}