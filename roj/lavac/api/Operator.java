package roj.lavac.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作符重载, <any> operator +(Inst inst, <any> target)
 * @author Roj234
 * @since 2022/10/23 0023 13:27
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Operator {
	Type value();

	enum Type {
		ADD, SUB, MULTIPLY, DIVIDE
	}
}
