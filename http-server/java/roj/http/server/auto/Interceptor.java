package roj.http.server.auto;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Roj234
 * @since 2023/2/5 11:35
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Interceptor {
	String[] value() default "";
	/**
	 * 是否为全局拦截器，全局拦截器在整个OKRouter实例都可用，否则只能在注册的对象中使用
	 */
	boolean global() default false;
}