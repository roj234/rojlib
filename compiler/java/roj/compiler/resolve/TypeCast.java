package roj.compiler.resolve;

import org.jetbrains.annotations.Range;
import roj.asm.Opcodes;
import roj.asm.insn.CodeWriter;
import roj.asm.type.*;
import roj.collect.ArrayList;
import roj.collect.ToIntMap;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Types;
import roj.compiler.types.CompoundType;
import roj.compiler.types.VirtualType;
import roj.text.CharList;
import roj.util.OperationDone;

import java.util.Collections;
import java.util.Map;

import static roj.asm.type.ParameterizedType.*;
import static roj.asm.type.Type.OBJECT;
import static roj.asm.type.Type.VOID;

/**
 * 类型转换
 * @author Roj234
 * @since 2023/5/18 11:11
 */
public class TypeCast {
	// 成功: 返回总distance最小的结果
	// 失败: 返回的type最小(差)的结果
	// 泛型成功只返回0

	// 成功
	public static final int UPCAST = 0, NUMBER_UPCAST = 1, UNBOXING = 2, BOXING = 3;
	// 失败
	public static final int IMPLICIT = -1, LOSSY = -2, DOWNCAST = -3;
	// 致命错误
	public static final int TO_PRIMITIVE = -4, FROM_PRIMITIVE = -5, GENERIC_PARAM_COUNT = -6, UNDEFINED = -7, IMPOSSIBLE = -8;

	private static final int DISTANCE_BOXING = 1;

	public TypeCast(Resolver context) {this.context = context;}

	public static class Cast {
		public int type;
		public int distance;

		public static final Cast IDENTITY = new Cast(0, 0);

		public byte box;
		byte op1, op2;
		IType target;
		boolean doCast;

		protected Cast(int t, int d) { type = t; distance = d; }

		//对于仅仅write的实例，节约空间
		public Cast intern() {return isNoop() ? IDENTITY : this;}

		// identity
		public boolean isIdentity() { return (type == UPCAST || type == DOWNCAST) && op1 == op2; }
		public boolean isNoop() { return (type == UPCAST || type == DOWNCAST) && target == null && op1 == op2; }

		public IType getTarget() { return target; }
		public byte getOp1() { return op1; }
		public byte getOp2() { return op2; }

		public String getCastDesc() {
			return switch (type) {
				case UPCAST -> "可以转换到";
				case NUMBER_UPCAST -> "向上转型到";
				case BOXING -> "装箱到";
				case UNBOXING -> "拆箱到";
				case IMPLICIT, LOSSY, DOWNCAST -> "强转到";
				default -> "无法转换到";
				case TO_PRIMITIVE -> "无法转换为基本类型";
				case FROM_PRIMITIVE -> "无法转换自基本类型";
				case UNDEFINED -> "数据不足";
				case GENERIC_PARAM_COUNT -> "泛型参数不同";
			};
		}

		@SuppressWarnings("fallthrough")
		public void write(CodeWriter cw) {
			switch (type) {
				default: throw new UnsupportedOperationException("'"+getCastDesc()+"'无法生成字节码");
				case NUMBER_UPCAST, IMPLICIT, LOSSY: break;
				case BOXING:
					writeOp(cw);
					writeBox(cw);
					return;
				case UNBOXING:
					cast(cw);
					cw.invoke(Opcodes.INVOKEVIRTUAL, Type.getWrapper(box).owner, Type.primitive(box).toString().concat("Value"), "()"+(char)box);
				break;
				case UPCAST, DOWNCAST:
					if (isNoop()) return;
					cast(cw);
				break;
			}

			writeOp(cw);
		}
		public void writeBox(CodeWriter cw) {
			Type wrapper = Type.getWrapper(box);
			cw.invoke(Opcodes.INVOKESTATIC, wrapper.owner, "valueOf", "("+(char)box+")L"+wrapper.owner+";");
		}
		private void writeOp(CodeWriter cw) {
			if (op1 != 0) {
				cw.insn(op1);
				if (op2 != 0) cw.insn(op2);
			}
		}

		private void cast(CodeWriter cw) {
			if (target != null && doCast) cw.clazz(Opcodes.CHECKCAST, target.rawType().getActualClass());
		}

		Cast implicitCastTo(IType to) {
			target = to;
			doCast = true;
			return this;
		}

		Cast withType(IType type) {
			if (target == null)
				target = type;
			return this;
		}
	}

	// region 'macro'
	public static Cast RESULT(int type, int distance) { return new Cast(type, distance); }
	public static Cast IMPLICIT(int type, IType to) { return new Cast(type, 0).implicitCastTo(to); }
	private static final Cast[] ERR = new Cast[6];
	static {
		for (int i = IMPOSSIBLE; i <= DOWNCAST; i++) {
			ERR[i-IMPOSSIBLE] = new Cast(i, -1); // -1 : not applicable
		}
	}
	public static Cast ERROR(@Range(from = IMPOSSIBLE, to = DOWNCAST) int type) { return ERR[type-IMPOSSIBLE]; }
	//public static Cast IMPOSSIBLE(String reason) { return new Cast(IMPOSSIBLE, -1).withReason(reason); }
	private static Cast DOWNCAST(IType type) { return new Cast(DOWNCAST, -1).implicitCastTo(type); }
	private static Cast NUMBER(int type, int distance, int op1) {
		Cast cast = new Cast(type, distance);
		cast.op1 = (byte) op1;
		return cast; }
	private static Cast NUMBER_DOWNCAST(int distance, int op1, int op2) {
		Cast cast = new Cast(LOSSY, distance);
		cast.op1 = (byte) op1;
		cast.op2 = (byte) op2;
		return cast; }
	// endregion

	public final Resolver context;
	public Map<TypeVariableDeclaration, IType> genericContext = Collections.emptyMap();

	public Cast checkCast(IType from, IType to) { return checkCast(from, to, -1); }
	private Cast checkCast(IType from, IType to, int targetWildcardType) {
		if (from.equals(to)) return RESULT(UPCAST, 0).withType(to);

		int flags = 0;

		switch (from.kind()) {
			default -> throw OperationDone.NEVER;
			case SIMPLE_TYPE -> {}
			case PARAMETERIZED_TYPE -> {
				ParameterizedType fromPT = (ParameterizedType) from;
				from = fromPT.sub != null ? mergeSubClass(fromPT) : fromPT;
				flags |= 1;

				var tps = fromPT.typeParameters;
				if (tps.size() == 1 && tps.get(0) == Types.anyGeneric) flags |= 256;
			}
			case TYPE_VARIABLE -> {
				TypeVariable tvFrom = (TypeVariable) from;

				// or 999 ?
				if (to.equals(Types.OBJECT_TYPE)) return RESULT(UPCAST, tvFrom.array() + 1);

				for (IType bound : tvFrom.getDeclaration()) {
					// T extends Number -> T[] => Number[]
					IType copyFrom = bound;

					if (tvFrom.array() != 0) {
						copyFrom = copyFrom.clone();
						copyFrom.setArrayDim(copyFrom.array() + tvFrom.array());
					}

					Cast c = checkCast(copyFrom, to, targetWildcardType);
					if (c.type >= 0) return c;
				}

				return DOWNCAST(to);
			}
			case UNBOUNDED_WILDCARD -> {return DOWNCAST(to);}
			// 泛型 (raw + visual)
			case CAPTURED_WILDCARD -> {
				var wc = (CompoundType) from;
				// 取代后的显示类型
				IType visualType = wc.getBound();
				// 类型参数上界
				IType rawType = wc.getTraits().get(0);

				var cast = checkCast(visualType, to, targetWildcardType);
				// 如果不需要checkcast指令
				if (cast.type >= DOWNCAST && checkCast(rawType.rawType(), to, targetWildcardType).type >= 0) {
					return cast;
				}
				return cast.implicitCastTo(visualType);
			}
			case IDENTITY_TYPE -> {return ((VirtualType) from).castTo(to);}
			case ANY_TYPE -> {
				// diamond operator or <nullType>
				// 这些容器提供任何类型 (基本类型除外？)
				if (to.isPrimitive()) return ERROR(TO_PRIMITIVE);
				return RESULT(UPCAST, 0).implicitCastTo(to);
			}
			// intersection
			case BOUNDED_WILDCARD -> {
				var wc = (CompoundType) from;

				Cast r = null;
				var bounds = wc.getTraits();
				for (int i = 0; i < bounds.size(); i++) {
					r = checkCast(bounds.get(i), to, SUPER_WILDCARD);
					if (r.type >= 0) return r;
				}

				return r;
			}
			// union
			case UNION_TYPE -> {
				for (IType bound : ((CompoundType) from).getTraits()) {
					Cast cast = checkCast(bound, to, targetWildcardType);
					if (cast.type >= 0) return DOWNCAST(to);
				}
				return ERROR(IMPOSSIBLE);
			}
		}

		switch (to.kind()) {
			default -> throw OperationDone.NEVER;
			case SIMPLE_TYPE -> {}
			case PARAMETERIZED_TYPE -> {
				ParameterizedType toPT = (ParameterizedType) to;
				to = toPT.sub != null ? mergeSubClass(toPT) : toPT;
				targetWildcardType = toPT.wildcard;
				flags |= 2;
			}
			case TYPE_VARIABLE -> {
				// 这个口子只能在方法推断里开，其他时候不允许（吗？我并不是很确定）
				if (genericContext == null) return DOWNCAST(to);
				byte wildcard = ((TypeVariable) to).wildcard;
				return checkCast(from, Inferrer.substituteTypeVariables(to, genericContext), wildcard);
			}
			case UNBOUNDED_WILDCARD -> {return RESULT(UPCAST, 0);}
			case CAPTURED_WILDCARD -> to = ((CompoundType) to).getBound();
			case IDENTITY_TYPE -> {return ((VirtualType) to).castFrom(from);}
			case ANY_TYPE -> {
				if (from.isPrimitive()) {
					LavaCompiler.debugLogger().debug("E_INT_OBJ, {}, {}", from, to);
					//return ERROR(E_INT2OBJ);
				}
				return RESULT(UPCAST, 0).implicitCastTo(from);
			}
			case BOUNDED_WILDCARD -> {
				var wc = (CompoundType) to;
				var bounds = wc.getTraits();

				Cast result = checkCast(from, bounds.get(0), targetWildcardType);
				if (result.type != UPCAST && result.type != DOWNCAST) return result;

				// 交集类型，from必须能转换到所有的边界，不过第0个已经在genericCast中检查了
				for (int i = 1; i < bounds.size(); i++) {
					Cast r = checkCast(from, bounds.get(i), targetWildcardType);
					if (r.type < 0) return r;
				}

				if (result.type >= UNBOXING) {
					result.type ^= 1;
					result.target = Type.getWrapper(from.getActualType());
				} else if (result.distance != 0) {
					result.target = from;
				}
			}
			case UNION_TYPE -> {
				for (IType bound : ((CompoundType) to).getTraits()) {
					Cast cast = checkCast(from, bound, targetWildcardType);
					if (cast.type >= 0) return cast;
				}
				return ERROR(IMPOSSIBLE);
			}
		}

		Cast r = checkCast(from.rawType(), to.rawType(), targetWildcardType);
		if (r.type != UPCAST && r.type != DOWNCAST) return r;

		if ((flags&256) != 0) return r.type == UPCAST ? r.implicitCastTo(to) : r.withType(to);

		genericCastCheck:
		if (r.type == UPCAST && (flags & 2) != 0) {
			var fc = context.inferGeneric(from, to.owner());
			/*if (fc == null) {
				if ((flags & 1) == 0) break genericCastCheck;
				fc = ((ParameterizedType) from).typeParameters;
			}*/
			if (fc == null) throw new AssertionError();

			var tc = context.inferGeneric(to, from.owner());
			if (tc == null) {
				if ((flags & 2) == 0) break genericCastCheck;
				tc = ((ParameterizedType) to).typeParameters;
			}

			if (fc.size() != tc.size()) return ERROR(GENERIC_PARAM_COUNT);

			for (int i = 0; i < fc.size(); i++) {
				Cast v = checkCast(fc.get(i), tc.get(i), targetWildcardType);
				if (v.type != UPCAST) return ERROR(IMPOSSIBLE);
			}
		}

		return r.withType(to);
	}

	/**
	 * 仅用于比较，合并非静态泛型参数，WIP
	 */
	private static IType mergeSubClass(ParameterizedType gg) {
		CharList sb = new CharList();

		sb.append(gg.owner);

		boolean copy = false;
		ParameterizedType gg1 = new ParameterizedType();
		gg1.setArrayDim(gg.array());
		gg1.typeParameters = gg.typeParameters;
		gg1.wildcard = gg.wildcard;

		GenericSub sub = gg.sub;
		while (true) {
			sb.append('$').append(sub.owner);
			if (!sub.typeParameters.isEmpty()) {
				if (!copy) {
					// just a hack to check validity
					gg1.typeParameters = new ArrayList<>(gg1.typeParameters);
					copy = true;
				}
				gg1.typeParameters.addAll(sub.typeParameters);
			}

			if (sub.sub == null) break;
			sub = sub.sub;
		}

		gg1.owner = sb.toStringAndFree();

		return gg1;
	}

	private Cast checkCast(Type from, Type to, int wildcardType) {
		if (from.equals(to)) return RESULT(UPCAST, 0);
		if (from.type == VOID || to.type == VOID) return ERROR(IMPOSSIBLE);

		if (from.isPrimitive()) {
			// 装箱
			if (!to.isPrimitive()) {
				int box = from.type;
				Cast cast = checkCast(Type.getWrapper(box), to, wildcardType);

				// 让byte可以转换到Integer
				if (cast.type < UPCAST) {
					box = getWrappedPrimitive(to);
					if (box == 0) return ERROR(FROM_PRIMITIVE);

					//noinspection MagicConstant
					cast = checkCast(from, Type.primitive(box), wildcardType);
					if (cast.type < UPCAST) return ERROR(FROM_PRIMITIVE);
				}

				assert cast.type <= NUMBER_UPCAST;

				// possible: E_EXPLICIT_CAST E_NUMBER_DOWNCAST UPCAST NUMBER_UPCAST
				cast.type = BOXING;
				cast.distance += DISTANCE_BOXING;
				cast.box = (byte) box;
				return cast;
			}

			// 泛型
			if (wildcardType >= 0) return ERROR(IMPOSSIBLE);

			// 皆为基本
			int fCap = Type.getSort(from.type)-1;
			int tCap = Type.getSort(to.type)-1;
			// downcast
			if (fCap > tCap) {
				// 0是boolean
				if (fCap == 0) return ERROR(IMPOSSIBLE);

				int distance = fCap - tCap;
				//	L2I = (byte) 0X88,
				//	F2I = (byte) 0X8B, F2L = (byte) 0X8C,
				//	D2I = (byte) 0X8E, D2L = (byte) 0X8F, D2F = (byte) 0X90,
				if (fCap > 4) {
					int caster1 = (Opcodes.L2I-19) + (Math.max(tCap, 4) + fCap*3);
					if (tCap >= 4) return NUMBER(LOSSY, distance, caster1);

					//	I2B = (byte) 0X91, I2C = (byte) 0X92, I2S = (byte) 0X93,
					return NUMBER_DOWNCAST(distance, caster1, (Opcodes.I2B-1) + tCap);
				}

				return NUMBER(IMPLICIT, distance, (Opcodes.I2B-1) + tCap);
			}

			int distance = tCap - fCap;

			// 还在int范围里
			if (tCap <= 4) return RESULT(
					(fCap == 2 && tCap != 4)  // Char to Byte/Short
					|| tCap == 2              // Byte/Short/Int to Char
					? IMPLICIT : UPCAST, distance);

			//	I2L = 0x85, I2F = 0x86, I2D = 0x87,
			//	            L2F = 0x89, L2D = 0x8A,
			//	                        F2D = 0x8D,
			// Char to Long/Float/Double is OK (but might a warning ??)
			return NUMBER(fCap == 2 ? LOSSY : NUMBER_UPCAST, distance, (Opcodes.I2L-17) + (tCap + Math.max(fCap, 4)*3));
		} else if (to.isPrimitive()) {
			// 泛型
			if (wildcardType >= 0) return ERROR(IMPOSSIBLE);
			// 拆箱
			int primitive = getWrappedPrimitive(from);
			if (primitive == 0) return ERROR(TO_PRIMITIVE);

			Cast cast;
			if (primitive == to.type) {
				cast = RESULT(UNBOXING, DISTANCE_BOXING);
			} else {
				//noinspection MagicConstant
				cast = checkCast(Type.primitive(primitive), to, wildcardType);
				if (cast.type < LOSSY) return cast;
				assert cast.type <= NUMBER_UPCAST;

				cast.type = UNBOXING;
				cast.distance += DISTANCE_BOXING;
			}

			cast.box = to.type;
			return cast;
		}
		// 皆非

		// 反转 基本类型不适用于Generic
		if (wildcardType == SUPER_WILDCARD) {
			Type tmp = from;
			from = to;
			to = tmp;
		}

		// 数组继承
		int arrayDelta = from.array() - to.array();
		if (arrayDelta < 0) { // 转成更高维度 (包括从Object到)
			if (from.type != OBJECT) return ERROR(IMPOSSIBLE); // 基本类型不可能

			/*String[] x;
			x = (String[]) (String) null;
			x = (String[]) (Object) null;
			x = (String[]) (Serializable) null;*/
			if ("java/lang/Object".equals(from.owner) ||
				LavaCompiler.arrayInterfaces().contains(from.owner))
				return DOWNCAST(to);

			return ERROR(IMPOSSIBLE);
		}
		if (from.array() != 0) {
			if (arrayDelta > 0) {
				//int[]      [] t1 = null;
				//Object     [] t2 = t1;
				String owner = wildcardType == SUPER_WILDCARD ? from.owner : to.owner;
				if (owner == null) return ERROR(IMPOSSIBLE); // t2是基本类型数组

				if (owner.equals("java/lang/Object")) return RESULT(UPCAST, 2);
				if (LavaCompiler.arrayInterfaces().contains(owner)) return RESULT(UPCAST, 1);

				return ERROR(IMPOSSIBLE); // int[] 除了转换成数组实现的类不能变成任何其他东西
			} else {
				if (from.type != to.type) return ERROR(IMPOSSIBLE);

				// 维度相同的基本类型数组 （然而，若是这种情况，前面equals就会直接返回真）
				// if (from.type != CLASS) return RESULT(UPCAST, 0);

				// 维度相同的对象数组 => 判断能否继承
			}
		}

		// 类继承 此处：f.array == t.array
		// 被注释掉的部分会equals不用重复检测
		// if (wildcardType == EX_NONE) return /*from.owner.equals(to.owner) ? RESULT(UPCAST, 0) : */ERROR(E_NEVER);

		var fromClass = context.resolve(from.owner);
		var toClass = context.resolve(to.owner);

		if (fromClass == null || toClass == null) return ERROR(UNDEFINED);

		// 本来是不该发生的（前面检测了），但是说不定未来会支持class aliasing呢/doge
		if (fromClass == toClass) return RESULT(UPCAST, 0);

		int distance = context.getHierarchyList(fromClass).getOrDefault(toClass.name(), -1)&0xFFFF;
		if (distance != 0xFFFF) return RESULT(UPCAST, distance);

		// to.parent == null => !Object (alias object or ??)
		return (fromClass.modifier() & Opcodes.ACC_FINAL) == 0
					&& toClass.parent() != null
					&& (fromClass.parent() != null || fromClass.name().equals("java/lang/Object"))
				? DOWNCAST(to)
				: ERROR(IMPOSSIBLE);
	}

	private static final ToIntMap<Type> WRAPPER = new ToIntMap<>(9);
	static {
		for (int i = 0; i < 9; i++) {
			WRAPPER.putInt(Type.getWrapper(Type.getBySort(i)), Type.getBySort(i));
		}
	}
	public static int getWrappedPrimitive(IType self) {
		if (self instanceof CompoundType ax) self = ax.getBound();
		return WRAPPER.getOrDefault(self, 0);
	}
}