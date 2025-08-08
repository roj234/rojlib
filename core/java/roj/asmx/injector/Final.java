package roj.asmx.injector;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 最终字段标记
 * @implNote 被标注字段如果在注入类中被赋值，将抛出{@link WeaveException}
 * @author Roj234
 * @since 2023/10/9 19:27
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD})
public @interface Final {
	/**
	 * 是否在目标类中将对应字段设为final
	 * 与{@link Copy}注解配合使用时，可在目标类中创建final字段
	 */
	boolean setFinal() default false;
}
