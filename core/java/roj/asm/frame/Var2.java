package roj.asm.frame;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import roj.asm.ClassUtil;
import roj.asm.insn.Label;
import roj.asm.type.Type;
import roj.collect.HashSet;
import roj.util.ArrayUtil;
import roj.util.FastFailException;

import java.util.*;

/**
 * 表示Java虚拟机中的数值（局部变量或操作数栈中的元素）。
 * a.k.a VerificationType
 * 这是一个类型安全且包含额外元数据的类，用于跟踪变量的类型、所有者（对于引用类型）、
 * 以及与未初始化状态相关的字节码偏移量（BCI）。
 * <p>
 * 变量类型常量包括：
 * <ul>
 *     <li>{@code T_TOP}: 未知或占位符类型。</li>
 *     <li>{@code T_INT}: 32位整数。</li>
 *     <li>{@code T_FLOAT}: 32位浮点数。</li>
 *     <li>{@code T_DOUBLE}: 64位浮点数。</li>
 *     <li>{@code T_LONG}: 64位长整数。</li>
 *     <li>{@code T_NULL}: null引用。</li>
 *     <li>{@code T_UNINITIAL_THIS}: 未初始化的 'this' 引用（仅在构造函数中）。</li>
 *     <li>{@code T_REFERENCE}: 引用类型（对象、数组等）。</li>
 *     <li>{@code T_UNINITIAL}: 未初始化的引用（例如，通过 new 操作符创建但尚未构造的对象）。</li>
 * </ul>
 * <p>
 * 该类支持变量之间的类型合并（{@code mergeWith}）和通用类型计算（{@code join}），
 * @author Roj234
 * @since 2021/6/2 23:28
 */
public final class Var2 {
	public static final byte T_TOP = 0, T_INT = 1, T_FLOAT = 2, T_DOUBLE = 3, T_LONG = 4, T_NULL = 5, T_UNINITIAL_THIS = 6, T_REFERENCE = 7, T_UNINITIAL = 8;

	@Range(from = T_TOP, to = T_UNINITIAL)
	public byte type;
	public String owner;
	public int pc = -1;
	public Label pcLabel;

	private List<String> constraints = Collections.emptyList();

	public static final Var2
		TOP = new Var2(T_TOP),
		INT = new Var2(T_INT),
		LONG = new Var2(T_LONG),
		FLOAT = new Var2(T_FLOAT),
		DOUBLE = new Var2(T_DOUBLE),
		SECOND = new Var2(T_TOP); // long_2nd or double_2nd

	/**
	 * 根据给定的Java虚拟机类型（{@link Type}）创建一个新的 {@code Var2} 实例。
	 *
	 * @param type Java虚拟机类型。
	 * @return 对应的 {@code Var2} 实例。如果类型是void，则返回null。
	 * @throws IllegalStateException 如果遇到未预期的类型。
	 */
	public static Var2 of(Type type) {
		return switch (type.getActualType()) {
			case Type.VOID -> null;
			case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> INT;
			case Type.FLOAT -> FLOAT;
			case Type.DOUBLE -> DOUBLE;
			case Type.LONG -> LONG;
			case Type.CLASS -> new Var2(T_REFERENCE, type.getActualClass());
			default -> throw new IllegalStateException("Unexpected type: "+type);
		};
	}
	/**
	 * 根据给定的字节类型和类名（或描述符）创建一个新的 {@code Var2} 实例。
	 *
	 * @param type 变量的字节码表示的类型。
	 * @param klass 变量的类名或描述符。
	 * @return 对应的 {@code Var2} 实例。
	 * @throws IllegalStateException 如果遇到未预期的类型。
	 */
	public static Var2 of(byte type, CharSequence klass) {
		return switch (type) {
			case T_TOP -> TOP;
			case T_INT -> INT;
			case T_FLOAT -> FLOAT;
			case T_DOUBLE -> DOUBLE;
			case T_LONG -> LONG;
			case T_NULL -> new Var2(T_NULL);
			case T_REFERENCE, T_UNINITIAL, T_UNINITIAL_THIS -> new Var2(type, klass.toString());
			default -> throw new IllegalStateException("Unexpected type: "+type);
		};
	}

	public Var2(byte type) {this.type = type;}
	public Var2(byte type, String owner) {
		this.type = type;
		this.owner = Objects.requireNonNull(owner, "owner");
	}
	public Var2(Label pc) {
		type = T_UNINITIAL;
		pcLabel = pc;
	}

	public int bci() {return pcLabel == null ? pc : pcLabel.getValue();}

	/**
	 * 验证当前 {@code Var2} 对象与另一个 {@code Var2} 对象 {@code o} 的类型兼容性。
	 * 如果类型兼容，它可能更新当前对象（{@code this}）以反映更具体或合并后的类型（例如，当当前类型是 {@code T_NULL} 时，它可能被设置为 {@code o} 的类型）；
	 * 如果类型不兼容，则抛出 {@link FastFailException}。
	 * 该方法返回一个布尔值，指示当前对象是否被修改。
	 *
	 * @param o 要合并的另一个 {@code Var2} 对象。
	 * @return 如果当前对象被修改，则返回 {@code true}；否则返回 {@code false}。
	 * @throws FastFailException 如果变量类型不兼容且无法合并。
	 */
	boolean mergeWith(Var2 o) {
		if (o == this) return false;

		if (o.type < T_NULL || type < T_NULL) {
			if (type != o.type) throw new FastFailException("无法合并 "+this+" 与 "+o);
			return false;
		}

		if (o.type == T_NULL) return false;
		if (type == T_NULL) { set(o); return true; }

		var owner = this.owner;
		if ((owner.equals(o.owner) && constraints.isEmpty()) || o.owner.equals("java/lang/Object")) return false;
		if (owner.equals("java/lang/Object")) {set(o);return true;}

		if (constraints.contains(o.owner)) {
			if (!this.owner.equals(o.owner)) {
				var tmp = new ArrayList<>(constraints);
				tmp.remove(o.owner);
				tmp.add(0, o.owner);
				constraints = tmp;
				this.owner = o.owner;
				return true;
			}

			return false;
		}

		Set<String> classes = new HashSet<>();

		if (constraints.isEmpty()) classes.add(owner);
		else classes.addAll(constraints);

		if (o.constraints.isEmpty()) classes.add(o.owner);
		else classes.addAll(o.constraints);

		var commonChild = ClassUtil.getInstance().getCommonChild(classes);
		if (commonChild.isEmpty()) throw new FastFailException("无法合并 "+this+" 与 "+o);

		var cr = constraints;
		if (ArrayUtil.equals(cr, commonChild)) commonChild = cr;

		this.owner = commonChild.get(0);
		constraints = commonChild.size() == 1 ? Collections.emptyList() : commonChild;

		var changed = false;
		if (cr.isEmpty()) {
			if (commonChild.size() > 1 || !commonChild.get(0).equals(owner)) changed = true;
		} else {
			if (commonChild.size() != cr.size() || !copyTo(commonChild, classes).containsAll(cr)) changed = true;
		}
		return changed;
	}

	/**
	 * 计算当前 {@code Var2} 对象与另一个 {@code Var2} 对象 {@code o} 的泛化类型（即共同超类型或更一般的类型）。
	 *
	 * @param o 要进行泛化的另一个 {@code Var2} 对象。
	 * @return 返回一个新的 {@code Var2} 对象表示泛化结果（如共同祖先类），
	 *         或者返回 {@code null} 表示无需泛化（例如类型相同或已经是通用类型）。
	 *         该方法不修改当前对象，而是创建新对象。
	 */
	Var2 join(@NotNull Var2 o) {
		if (o == this) return null;

		if (o.type < 5 || type < 5) return type != o.type && this != TOP ? TOP : null;

		if (o.type == T_NULL) return null;
		if (type == T_NULL) return o;

		if (type != o.type) return TOP;

		// 没法更common了
		if ((constraints.isEmpty() ? owner.equals(o.owner) : ArrayUtil.equals(constraints, o.constraints)) || owner.equals("java/lang/Object")) {
			return null;
		}
		// o更不具体
		if (o.owner.equals("java/lang/Object")) return o;

		var classes1 = new HashSet<String>();
		var classes2 = new HashSet<String>();

		if (constraints.isEmpty()) classes1.add(owner);
		else classes1.addAll(constraints);

		if (o.constraints.isEmpty()) classes2.add(o.owner);
		else classes2.addAll(o.constraints);

		List<String> commonAncestors = ClassUtil.getInstance().getCommonAncestors(classes1, classes2);
		if (commonAncestors.size() == 0) throw new IllegalStateException("找不到"+classes1+"或"+classes2+"的类");

		List<String> cr = constraints;
		if (ArrayUtil.equals(cr, commonAncestors)) commonAncestors = cr;

		Set<String> classes = ClassUtil.getInstance().getSharedSet();
		if (!cr.isEmpty()
				? commonAncestors.size() != cr.size() || !copyTo(commonAncestors, classes).containsAll(cr)
				: commonAncestors.size() != 1 || !owner.equals(commonAncestors.get(0))) {

			var copy = copy();
			copy.owner = commonAncestors.get(0);
			copy.constraints = commonAncestors.size() == 1 ? Collections.emptyList() : commonAncestors;

			if (FrameVisitor.debug) System.out.println("Join "+this+" | "+o+" => "+copy);
			if (copy.equals(o) && constraints.isEmpty()) return o;

			return copy;
		}

		return null;
	}

	private Collection<String> copyTo(List<String> values, Set<String> set) {
		if (values.size() < 4) return values;
		set.clear();
		set.addAll(values);
		return values;
	}

	public void set(Var2 o) {
		type = o.type;
		pc = o.pc;
		pcLabel = o.pcLabel;
		owner = o.owner;
		constraints = o.constraints;
	}

	public Var2 copy() {
		if (type <= T_NULL) return this;
		Var2 c = new Var2(type);
		c.set(this);
		return c;
	}

	public boolean equals(Object o) {
		if (this == o) return true;
		if (o instanceof Var2 v && v.type == type) {
			if (owner != null) {
				return owner.equals(v.owner) && ArrayUtil.equals(constraints, v.constraints);
			} else if (v.owner == null) {
				return v.pc == this.pc;
			}
		}
		return false;
	}

	@Override
	public int hashCode() {
		return toString().hashCode();
	}

	private static final String[] toString = {"top", "int", "float", "double", "long", "(null)", "uninitial_this", "object", "uninitial"};
	public String toString() {
		if (type == T_UNINITIAL || type == T_UNINITIAL_THIS) {
			return "未初始化"+(owner==null?"":"("+owner+")")+" #"+bci()+" @"+Integer.toHexString(System.identityHashCode(this));
		} else if (type == T_REFERENCE) {
			return owner+(!constraints.isEmpty()? constraints :"")+"@"+Integer.toHexString(System.identityHashCode(this));
		} else {
			return this == SECOND ? "_2nd" : toString[type];
		}
	}
}