package roj.mod.agent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2024/6/28 0028 22:20
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Emulated {}