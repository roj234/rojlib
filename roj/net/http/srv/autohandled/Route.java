package roj.net.http.srv.autohandled;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/2/5 0005 11:35
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Route {
	/**
	 * 留空以使用 [方法名].replace('__', '/')
	 */
	String value() default "啊随便写点什么反正我也不会用Proxy访问注解的";
	String validator() default "";
	Type type() default Type.ASIS;

	enum Type {
		ASIS, PREFIX, REGEXP
	}
}
