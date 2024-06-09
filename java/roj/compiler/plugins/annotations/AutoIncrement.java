package roj.compiler.plugins.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 未实现，计划是替代XXLexer中硬编码的short id
 * @author Roj234
 * @since 2024/4/12 0012 19:19
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface AutoIncrement {
	int value() default 0;
	int step() default 1;
}