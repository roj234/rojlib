package roj.compiler.plugins.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * 使用 {@link ConstantBootstraps#invoke(MethodHandles.Lookup, String, Class, MethodHandle, Object...)} (loadDynamic) 处理这个函数，
 * 用于替代double check lock或者其它方式，可能会有更好的性能， (Require Java11+)
 * 并避免被反射更改（雾
 * <pre>{@code
 * @Singleton("getInstance")
 * private static String testMethod1() {return new String("好复杂的对象啊，初始化就要114ms");}
 *
 * {
 * 	System.out.println(getInstance() == getInstance()); // returns true
 * }
 *
 * }</pre>
 * @author Roj234
 * @since 2024/6/16 10:00
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface Singleton {
	/**
	 * Getter的名称，这么设计是为了drop-in replace，也就是说，你可以在Getter内实现正常的DCL，因为Singleton会替换这个函数
	 */
	String value();
}