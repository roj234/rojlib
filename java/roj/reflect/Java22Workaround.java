package roj.reflect;

import roj.plugins.ci.annotation.InRepo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 所有标记了这个注解的都应该在运行时处于public状态，以适配Java22+
 * @author Roj234
 * @since 2025/3/19 15:22
 */
@InRepo
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
public @interface Java22Workaround {}
