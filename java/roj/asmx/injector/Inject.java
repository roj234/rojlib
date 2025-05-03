package roj.asmx.injector;

import org.intellij.lang.annotations.MagicConstant;
import roj.asm.MemberNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Inject {
	/**
	 * 目标方法名称
	 * @return '/'表示使用当前方法名，空字符串表示由{@code NiximHelper}决定
	 */
	String value() default "";
	/**
	 * 目标方法签名（留空表示与当前方法相同）
	 * 例如 "(Ljava/lang/String;)V"
	 */
	String injectDesc() default "";

	/**
	 * 注入位置
	 */
	enum At {
		/** 在方法开头注入 */
		HEAD,
		/** 在方法返回前注入 */
		TAIL,
		/** 完全替换原方法 */
		REPLACE,
		/**
		 * 传统invokespecial注入方式
		 * @apiNote 允许通过super.method调用原方法，可在其前后插入代码。<br>
		 * • 相比HEAD/TAIL模式具有更低开销<br>
		 * • 与其他字节码注入工具可能存在兼容性问题<br>
		 * • 默认注入方式
		 */
		SUPER,
		/**
		 * 移除原方法
		 * @implNote 被注解的方法必须声明为抽象方法
		 */
		REMOVE,
	}

	/**
	 * 指定代码注入位置
	 * @see At
	 */
	At at() default At.SUPER;

	/**
	 * 允许目标方法不存在（跳过注入不报错）
	 */
	int OPTIONAL = 1;
	/**
	 * 在运行时进行名称映射
	 * @see CodeWeaver#mapName(String, String, String, MemberNode)
	 */
	int RUNTIME_MAP = 2;

	/**
	 * 配置标志位
	 * @see #OPTIONAL
	 * @see #RUNTIME_MAP
	 */
	@MagicConstant(flagsFromClass = Inject.class)
	int flags() default 0;
}