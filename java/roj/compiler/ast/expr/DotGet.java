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
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.FieldResult;
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

	private byte flags;
	private MyBitSet bits = EMPTY_BITS;

	private IClass begin;
	private FieldNode[] chain;
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
			this.flags = (byte) type;
		} else {
			this.flags = -128;
		}
	}

	public Type toClassRef() {
		if (flags >= 0) throw new IllegalArgumentException("cannot be class ref");

		CharList sb = LocalContext.get().tmpList; sb.clear();
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
	public ExprNode resolve(LocalContext ctx) throws ResolveException { return resolveEx(ctx, null); }
	@Contract("_, null -> !null")
	final ExprNode resolveEx(LocalContext ctx, Invoke invoke) throws ResolveException {
		if ((flags&CHECKED) != 0) return this;

		// 用于错误提示
		FieldResult inaccessibleThis = null;

		if (flags < 0) {
			// 首先我们要拿到第一个field
			// 这里按顺序处理下列情况

			String part = names.get(0);

			Variable varParent = ctx.tryVariable(part);
			if (varParent != null) {
				// 1. 变量
				parent = new LocalVariable(varParent);
				if (names.size() == 1) return parent;

				names.remove(0);
				flags = 0;
			} else {
				var fields = ctx.get_frChain(); fields.clear();
				if ((begin = ctx.tryImportField(part, fields)) != null) {
					// 2. 静态字段导入
					names.set(0, fields.get(0).name());
					flags = 0;
				} else {
					// 3. 省略this的当前类字段（包括继承！）
					// * => 在错误处理中需要二次检查以生成更有帮助的错误信息（static）

					ComponentList fieldList = ctx.fieldListOrReport(ctx.file, part);
					if (fieldList != null) {
						inaccessibleThis = fieldList.findField(ctx, ctx.in_static ? ComponentList.IN_STATIC : 0);
						if (inaccessibleThis.error == null) {
							parent = ctx.ep.This();
							flags = 0;
						}
					}
				}
			}

			// 4. 前缀包名.字段
		}

		String part = names.get(0);
		int i = 0;
		CharList sb = ctx.tmpList; sb.clear();
		while (true) {
			sb.append(part);
			if (++i == names.size()) break;
			if ((part = names.get(i)).equals(";[")) throw new ResolveException("symbol.error.arrayBrOutsideClassRef");
			sb.append('/');
		}

		if (flags >= 0) { // parent不为null, 下面是处理importField
			IType fType;
			IClass symbol;

			if (parent == null) {
				fType = new Type(begin.name());
				symbol = begin;
			} else {
				fType = (parent = parent.resolve(ctx)).type();
				if (fType.isPrimitive()) {
					ctx.report(Kind.ERROR, "symbol.error.derefPrimitiveField", fType);
					return this;
				}

				symbol = ctx.getClassOrArray(fType);
				if (symbol == null) throw new ResolveException("symbol.error.noSuchClass:"+fType);
			}

			String error = ctx.resolveField(symbol, fType, sb);
			if (error != null) {
				ctx.report(Kind.ERROR, error);
				return this;
			}

			begin = ctx.get_frBegin();
			chain = ctx.get_frChain().toArray(new FieldNode[0]);
			type = ctx.get_frType();

			i = 1;
			part = fType.owner();
		} else {
			assert chain == null;
			// 4. 前缀包名.字段 ^
			// ^ => 我决定采用（已经设计好的）这种机制，虽然可能没必要

			String error = ctx.resolveDotGet(sb, invoke != null);

			if (error != null) {
				if (error.isEmpty()) {
					assert invoke != null;
					invoke.fn = ctx.get_frBegin();
					return null;
				}

				if (inaccessibleThis != null) error = inaccessibleThis.error;
				ctx.report(Kind.ERROR, error);
				return this;
			}

			begin = ctx.get_frBegin();
			chain = ctx.get_frChain().toArray(new FieldNode[0]);
			type = ctx.get_frType();

			i = 1;
			part = chain[0].fieldType().owner();
		}

		flags = CHECKED;

		assert chain != null;

		int length = chain.length;
		FieldNode last = chain[length-1];

		// length不是字段而是opcode
		if (last == GlobalContext.arrayLength()) {
			flags |= FINAL_FIELD|ARRAY_LENGTH;
			type = Type.std(Type.INT);
			length--;
		} else if (type == null) {
			// get_frType只处理泛型
			type = last.fieldType();
		}

		FieldNode fn;
		if (i < length) for (;;) {
			fn = chain[i];
			if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
				ctx.report(Kind.SEVERE_WARNING, "symbol.warn.static_on_half", part, fn.name(), "symbol.field");
			}

			if (++i == length) break;
			part = fn.fieldType().owner();
		} else  {
			fn = chain[0];
			assert chain.length == 1;

			// 替换常量 如你所见只有直接访问(<class>.<field>)才会替换,如果中途使用了非静态字段会警告，👆
			if (!ctx.disableConstantValue) {
				Object c = ctx.getConstantValue(begin, fn);
				// 大概也用不到泛型... 不过还是留着
				if (c != null) return new Constant(type == null ? fn.fieldType() : type, c);
			}
		}

		if (bits != EMPTY_BITS) {
			flags |= FINAL_FIELD;
			if ((flags&ARRAY_LENGTH) != 0) ctx.report(Kind.ERROR, "dotGet.error.illegalArrayLength");
		} else if ((fn.modifier&Opcodes.ACC_FINAL) != 0) flags |= FINAL_FIELD;
		if (part != null && part.equals(ctx.file.name)) flags |= SELF_FIELD;
		return this;
	}

	@Override
	public IType type() {return type == null ? Asterisk.anyType : type;}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		int length = chain.length - (flags&ARRAY_LENGTH);
		write0(cw, length);
		if (length != chain.length) cw.one(Opcodes.ARRAYLENGTH);
	}

	@Override
	public boolean isFinal() { return (flags&FINAL_FIELD) != 0; }

	@Override
	public void preStore(MethodWriter cw) {
		if ((flags&SELF_FIELD) != 0) cw.ctx1.checkSelfField(chain[chain.length-1], true);
		write0(cw, chain.length-1);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		preStore(cw);
		if (!isStaticField()) cw.one(Opcodes.DUP);
	}

	@Override
	public void postStore(MethodWriter cw) {
		FieldNode fn = chain[chain.length-1];
		String owner = chain.length == 1 ? begin.name() : chain[chain.length-2].fieldType().owner();
		cw.field((fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, owner, fn.name(), fn.rawDesc());
	}

	@Override
	public void copyValue(MethodWriter cw, boolean twoStack) {
		cw.one(isStaticField() ? twoStack ? Opcodes.DUP2 : Opcodes.DUP : twoStack ? Opcodes.DUP2_X1 : Opcodes.DUP_X1);
	}

	@Override
	public boolean canBeReordered() { return parent == null && isStaticField(); }
	private boolean isStaticField() { return chain.length == 1 && (chain[0].modifier & Opcodes.ACC_STATIC) != 0; }

	private void write0(MethodWriter cw, int length) {
		if ((flags&SELF_FIELD) != 0) cw.ctx1.checkSelfField(chain[chain.length-1], false);

		int i = 0;
		String owner = chain.length == 1 ? begin.name() : chain[chain.length-2].fieldType().owner();
		FieldNode fn = chain[0];
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
			fn = chain[i];

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