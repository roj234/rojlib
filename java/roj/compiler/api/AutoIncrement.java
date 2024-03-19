package roj.compiler.api;

/**
 * 未实现，计划是替代XXLexer中硬编码的short id
 * @author Roj234
 * @since 2024/4/12 0012 19:19
 */
public @interface AutoIncrement {
	String previous() default "";
}