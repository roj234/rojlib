package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2025/4/26 3:04
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.PARAMETER)
public @interface QueryParam {
	String value() default "";
	/**
	 * 留空：参数必填
	 * 设置字符串：参数可选，不存在时使用该字符串
	 */
	String orDefault() default "";
}