package roj.compiler.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.ConstantBootstraps;

/**
 * 尝试使用 {@link ConstantBootstraps} 处理这个字段，
 * 以替代double check lock对应方法中的代码，
 * 并避免被反射更改（雾
 * @author Roj234
 * @since 2024/6/16 0016 10:00
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.FIELD)
public @interface Frozen {
	String legacyLockingMethod();
}