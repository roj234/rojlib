package roj.kscript.type;

import roj.kscript.api.IArray;
import roj.kscript.api.IObject;
import roj.kscript.asm.Frame;
import roj.kscript.func.KFunction;
import roj.kscript.parser.ast.Expression;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since 2020/10/27 13:13
 */
public interface KType {
	default <T> KJavaObject<T> asJavaObject(Class<T> clazz) {
		throw new ClassCastException(getType() + " cannot cast to " + Type.JAVA_OBJECT);
	}

	Type getType();

	default int asInt() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.INT);
	}
	default void setIntValue(int v) {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.INT);
	}

	default double asDouble() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.DOUBLE);
	}
	default void setDoubleValue(double v) {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.DOUBLE);
	}

	@Nonnull
	default String asString() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.STRING);
	}

	@Nonnull
	default IObject asObject() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.OBJECT);
	}

	@Nonnull
	default KObject asKObject() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.OBJECT);
	}

	@Nonnull
	default IArray asArray() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.ARRAY);
	}

	default KFunction asFunction() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.FUNCTION);
	}

	default boolean asBool() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.BOOL);
	}

	default KError asKError() {
		throw new ClassCastException(getType().name() + ' ' + this + " cannot cast to " + Type.ERROR);
	}

	StringBuilder toString0(StringBuilder sb, int depth);


	/**
	 * 仅用于{@link Expression#compress() Expression的缩减}以及{@link roj.kscript.asm.LoadDataNode#exec(Frame) 加载KType}中 <BR>
	 * 拷贝自身, 用于加载对象, 嗯
	 */
	default KType copy() {
		return this;
	}

	/**
	 * 仅用于{@link roj.kscript.asm.LoadDataNode#exec(Frame) 加载KType}中 <BR>
	 * 从同类对象中拷贝数据
	 */
	default void copyFrom(KType type) {}

	boolean canCastTo(Type type);

	default boolean equalsTo(KType b) {
		return equals(b);
	}

	default boolean isInt() {
		return false;
	}

	default boolean isString() {
		return false;
	}

	/**
	 * kind =-1: 从变量返回栈上 <br>
	 * kind = 0: 作为本地变量 <br>
	 * kind = 1: 作为方法参数 <br>
	 * kind = 2: 复制 <br>
	 * kind = 3: 从栈去外部 <br>
	 * kind = 4: 从外部进入栈 <br>
	 * kind = 5: 引用计数+1 <br>
	 * kind = 6: 引用计数-1
	 */
	default KType memory(int kind) {
		return this;
	}
}
