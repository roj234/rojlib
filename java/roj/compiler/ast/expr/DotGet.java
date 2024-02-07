package roj.compiler.ast.expr;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.Label;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.context.ClassContext;
import roj.compiler.context.CompileContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.text.CharList;
import roj.text.TextUtil;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2022/2/27 20:27
 */
final class DotGet extends VarNode {
	private static final MyBitSet EMPTY_BITS = new MyBitSet();

	ExprNode parent;
	SimpleList<String> names;

	private byte state;
	private MyBitSet bits = EMPTY_BITS;
	private Object[] fch;
	private IType type;

	private static final byte ARRAY_LENGTH = 1, FINAL_FIELD = 2, SELF_FIELD = 4, CHECKED = 8;

	// parent可以是Constant This Super ArrayGet Invoke ArrayDef New
	private static final ToIntMap<Class<?>> TypeId = new ToIntMap<>();
	static {
		TypeId.putInt(This.class, 0);
		TypeId.putInt(Invoke.class, 1);
		TypeId.putInt(ArrayGet.class, 2);
		TypeId.putInt(ArrayDef.class, 3);
		TypeId.putInt(Constant.class, 4);
		TypeId.putInt(Cast.class, 5);
		TypeId.putInt(EncloseRef.class, 6);
	}

	public DotGet(@Nullable ExprNode parent, String name, int flag) {
		this.parent = parent;
		this.names = new SimpleList<>(4);
		this.names.add(name);
		if (flag != 0) {
			bits = new MyBitSet();
			bits.add(1);
		}

		if (parent != null) {
			int type = TypeId.getOrDefault(parent.getClass(), -1);
			if (type == -1) throw new IllegalArgumentException("未识别的parent:"+parent.getClass().getName());
			this.state = (byte) type;
		} else {
			this.state = -128;
		}
	}

	public Type toClassRef() {
		if (state >= 0) throw new IllegalArgumentException("cannot be class ref");

		CharList sb = CompileContext.get().tmpList; sb.clear();
		int i = 0;
		String part = names.get(0);
		while (true) {
			sb.append(part);
			if (++i == names.size() || (part = names.get(i)).equals(";[")) break;
			sb.append('/');
		}

		int pt = i == 1 ? TypeHelper.toPrimitiveType(names.get(0)) : -1;
		return pt > 0 ? new Type(pt, names.size()-i) : new Type(sb.toString(), names.size()-i);
	}

	@Override
	public String toString() {
		CharList sb = new CharList();
		if (parent != null) sb.append(parent).append('.');
		return sb.append(TextUtil.join(names, ".")).toStringAndFree();
	}

	@NotNull
	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException { return resolveEx(ctx, null); }
	@Contract("_, null -> !null")
	final ExprNode resolveEx(CompileContext ctx, Invoke invoke) throws ResolveException {
		if ((state&CHECKED) != 0) return this;

		String part = names.get(0);
		if (parent == null && names.size() == 1) {
			// LocalVariable Ref
			Variable variable = ctx.tryVariable(part);
			if (variable != null) return new LocalVariable(variable);

			Object[] field = ctx.tryImportField(part);
			if (field != null) fch = field;
		}

		int i = 0;
		CharList sb = ctx.tmpList; sb.clear();
		while (true) {
			sb.append(part);
			if (++i == names.size()) break;
			if ((part = names.get(i)).equals(";[")) throw new ResolveException("symbol.error.arrayBrOutsideClassRef");
			sb.append('/');
		}

		IType fType;
		if (state >= 0) {
			parent = parent.resolve(ctx);

			fType = parent.type();
			if (fType.isPrimitive()) {
				ctx.report(Kind.ERROR, "symbol.error.derefPrimitiveField", fType);
				return this;
			}

			IClass symbol = ctx.getClassOrArray(fType);
			if (symbol == null) throw new ResolveException("symbol.error.noSuchClass:"+fType);

			String error = ctx.resolveField(symbol, fType, sb);
			if (error != null) {
				ctx.report(Kind.ERROR, error);
				return this;
			}

			type = (IType) ctx.popLastResolveResult();
			fch = ctx.fieldResolveResult();
			i = 1;
			part = fType.owner();
		} else {
			if (fch == null) {
				String error = ctx.resolveClassField(sb, invoke != null);
				if (error != null) {
					if (error.isEmpty()) {
						assert invoke != null;
						invoke.fn = ctx.popLastResolveResult();
						return null;
					}

					ctx.report(Kind.ERROR, error);
					return this;
				}

				fch = ctx.fieldResolveResult();
			}

			i = 2;
			part = ((FieldNode) fch[1]).fieldType().owner();
		}
		state = CHECKED;

		assert fch != null;

		int length = fch.length;
		Object last = fch[length-2];
		// 数组长度分支，数组实现了一些接口，也有对应的方法，但是length不是字段而是opcode
		if (sb.endsWith("length")) {
			if(last.getClass() == FieldNode.class ? ((FieldNode) last).fieldType().array() > 0 : last == ClassContext.anyArray()) {
				state |= FINAL_FIELD|ARRAY_LENGTH;
				length--;
			}
		}

		FieldNode fn;
		if (i < length) for (;;) {
			fn = (FieldNode) fch[i];
			if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
				ctx.report(Kind.SEVERE_WARNING, "symbol.warn.static_on_half", part, fn.name(), "symbol.field");
			}

			if (++i == length) break;
			part = fn.fieldType().owner();
		} else  {
			fn = (FieldNode) fch[1];
			assert fch.length == 2;

			// 替换常量 如你所见只有直接访问(<class>.<field>)才会替换,如果中途使用了非静态字段会警告，👆
			if (!ctx.disableConstantValue) {
				Object c = ctx.getConstantValue((IClass)fch[0], fn);
				// 大概也用不到泛型... 不过还是留着
				if (c != null) return new Constant(type == null ? fn.fieldType() : type, c);
			}
		}

		if (bits != EMPTY_BITS) {
			state |= FINAL_FIELD;
			if ((state&ARRAY_LENGTH) != 0) ctx.report(Kind.ERROR, "dotGet.error.illegalArrayLength");
		} else if ((fn.modifier&Opcodes.ACC_FINAL) != 0) state |= FINAL_FIELD;
		if (part != null && part.equals(ctx.file.name)) state |= SELF_FIELD;
		return this;
	}

	@Override
	public IType type() {
		// array length
		if ((state&ARRAY_LENGTH) != 0) return Type.std(Type.INT);

		if (type != null) return type;
		if (fch == null) return Asterisk.anyType;
		return ((FieldNode) fch[fch.length-1]).fieldType();
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		int length = fch.length - (state&ARRAY_LENGTH);
		write0(cw, length);
		if (length != fch.length) cw.one(Opcodes.ARRAYLENGTH);
	}

	@Override
	public boolean isFinal() { return (state&FINAL_FIELD) != 0; }

	@Override
	public void preStore(MethodWriter cw) {
		if ((state&SELF_FIELD) != 0) cw.ctx1.checkSelfField((FieldNode)fch[fch.length-1], true);
		write0(cw, fch.length-1);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		preStore(cw);
		if (!isStaticField()) cw.one(Opcodes.DUP);
	}

	@Override
	public void postStore(MethodWriter cw) {
		FieldNode fn = (FieldNode) fch[fch.length-1];
		String owner = fch.length == 2 ? ((IClass) fch[0]).name() : ((FieldNode) fch[fch.length - 2]).fieldType().owner();
		cw.field((fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, owner, fn.name(), fn.rawDesc());
	}

	@Override
	public void copyValue(MethodWriter cw, boolean twoStack) {
		cw.one(isStaticField() ? twoStack ? Opcodes.DUP2 : Opcodes.DUP : twoStack ? Opcodes.DUP2_X1 : Opcodes.DUP_X1);
	}

	@Override
	public boolean canBeReordered() { return parent == null && isStaticField(); }
	private boolean isStaticField() { return fch.length == 2 && (((FieldNode) fch[1]).modifier & Opcodes.ACC_STATIC) != 0; }

	private void write0(MethodWriter cw, int length) {
		if ((state&SELF_FIELD) != 0) cw.ctx1.checkSelfField((FieldNode)fch[fch.length-1], false);

		int i = 1;
		String owner = fch.length == 2 ? ((IClass) fch[0]).name() : ((FieldNode) fch[fch.length-2]).fieldType().owner();
		FieldNode fn = (FieldNode) fch[1];
		byte opcode;

		boolean hasParent = parent != null;
		if (hasParent) parent.write(cw, false);

		if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
			opcode = Opcodes.GETSTATIC;
			if (hasParent) cw.one(Opcodes.POP); // field is useless
		} else {
			opcode = Opcodes.GETFIELD;
		}

		if (i == length) return;

		Label ifNull = bits == EMPTY_BITS ? null : new Label();
		for (;;) {
			if (bits.contains(i)) {
				cw.one(Opcodes.DUP);
				cw.jump(Opcodes.IFNULL, ifNull);
			}

			cw.field(opcode, owner, fn.name(), fn.rawDesc());
			if (++i == length) break;

			owner = fn.fieldType().owner();
			fn = (FieldNode) fch[i];

			if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
				opcode = Opcodes.GETSTATIC;
				cw.one(Opcodes.POP); // field is useless
			} else {
				opcode = Opcodes.GETFIELD;
			}
		}

		if (ifNull != null) cw.label(ifNull);
	}

	public DotGet add(String name, int flag) {
		names.add(name);
		if (flag != 0) {
			if (bits.size() == 0) bits = new MyBitSet();
			bits.add(names.size());
		}
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DotGet get = (DotGet) o;

		if (parent != null ? !parent.equals(get.parent) : get.parent != null) return false;
		return names.equals(get.names);
	}

	@Override
	public int hashCode() {
		int result = parent.hashCode();
		result = 31 * result + names.hashCode();
		result = 31 * result + bits.hashCode();
		return result;
	}
}