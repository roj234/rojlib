package roj.compiler.api;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 允许Literally ’堆叠‘在属性中，而不是{@link java.lang.annotation.Repeatable}
 * @author Roj234
 * @since 2024/6/21 20:06
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
public @interface Stackable {}