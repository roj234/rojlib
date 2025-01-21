package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要实现T get(int)和int size()
 * @author Roj234
 * @since 2024/6/30 0030 18:11
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ListIterable {}