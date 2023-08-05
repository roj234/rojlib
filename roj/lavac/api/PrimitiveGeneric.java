package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 支持基本类型的泛型
 * @author Roj233
 * @since 2021/9/2 21:49
 */
public class PrimitiveGeneric {
	/**
	 * 该类的泛型支持基本类型
	 * 所有用到泛型的方法需要{@link Method}
	 */
	@Retention(RetentionPolicy.CLASS)
	//@Target(ElementType.TYPE_PARAMETER)
	@Target(ElementType.TYPE)
	public @interface User {
		/** 应用到的类型参数 */
		String[] to();
		/** 类型参数支持的基本类型 */
		Class<?>[] type();
		/** 实现方法 */
		int mode() default 0;
	}

	@Retention(RetentionPolicy.CLASS)
	@Target(ElementType.METHOD)
	public @interface Method {
		/** 为每一种写在User#type的基本类型，按顺序写下它们实现函数的名称 */
		String[] value();
	}
}
