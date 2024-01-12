package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作符重载
 * 静态模式：
 * 一元运算符: static TYPE some_method(LEFT_TYPE left)
 * 二元运算符: static TYPE some_method(LEFT_TYPE left, RIGHT_TYPE right)
 * 找不到适合的方法时会尝试反转left和right
 * 动态模式：
 * TYPE some_method()
 * TYPE some_method(RIGHT_TYPE right)
 * 不支持反转(这是为了逻辑更清晰)
 * @author Roj234
 * @since 2022/10/23 0023 13:27
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Operator {
	/**
	 * + - / * ! ~ & | ^ && || ARRAY_GET ARRAY_SET <br>
	 * 甚至是【自定义运算】 比如 a !!! b 甚至 a myplus b (注册标识符)
	 */
	String value();
	/** 元： 1或2 */
	int ary() default 2;
	/** 允许对该方法反转left和right */
	boolean invert() default true;
}