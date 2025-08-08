package roj.asmx;

import roj.asm.MemberDescriptor;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.text.CharList;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/8/4 15:36
 */
public abstract class MethodHook implements Transformer {
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected @interface RealDesc { String[] value(); boolean callFrom() default false; }

	protected final HashMap<MemberDescriptor, MemberDescriptor> hooks = new HashMap<>();

	public MethodHook() { addDefaultHook(); }
	protected void addDefaultHook() {
		CharList sb = new CharList();
		for (Method m : getClass().getDeclaredMethods()) {
			if (m.getName().startsWith("hook_")) {
				int flag = Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC;
				if ((m.getModifiers()&flag) != flag) throw new IllegalArgumentException("hook方法必须公开静态:"+m);

				sb.clear();
				sb.append(m.getName());
				int i = sb.indexOf("__");
				if (i < 0) i = sb.lastIndexOf("_")+1;
				else i += 2;

				MemberDescriptor toDesc = new MemberDescriptor();
				toDesc.owner = this.getClass().getName().replace('.', '/');
				toDesc.name = m.getName();
				toDesc.rawDesc = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
				int i1 = sb.indexOf("static_");
				toDesc.modifier = (char) (i1>=0&&i1<=i ? 1 : 0);

				Function<MemberDescriptor, List<Object>> fn = (x) -> {
					List<Object> list = new ArrayList<>();
					list.add(toDesc);
					return list;
				};

				i1 = sb.indexOf("newInstance_");
				if (i1>=0&&i1<=i) toDesc.modifier |= 4;

				RealDesc altDesc = m.getAnnotation(RealDesc.class);
				if (altDesc != null) {
					if (altDesc.callFrom()) toDesc.modifier |= 2;
					for (String s : altDesc.value()) {
						MemberDescriptor key = MemberDescriptor.fromJavapLike(s);
						hooks.put(key, toDesc);
					}
				} else {
					if (toDesc.modifier == 0) {
						List<Type> param = Type.methodDesc(toDesc.rawDesc);
						String owner = param.remove(0).owner();

						i1 = sb.indexOf("callFrom_");
						if (i1>=0&&i1<=i) {
							param.remove(param.size()-2);
							toDesc.modifier |= 2;
						}

						MemberDescriptor key = toDesc.copy();
						key.owner = owner;
						key.name = sb.substring(i, sb.length());
						key.rawDesc = Type.toMethodDesc(param);

						hooks.put(key, toDesc);
					} else {
						throw new IllegalArgumentException("无法获取静态注入类,请使用@RealDesc:"+m);
					}
				}
			}
		}
	}

	@Override
	public boolean transform(String mappedName, Context ctx) {
		String self = ctx.getData().name();
		MemberDescriptor d = new MemberDescriptor();

		ctx.getData().visitCodes(new CodeWriter() {
			int stackSize;
			boolean hasLdc;

			@Override
			public void visitSize(int stackSize, int localSize) {
				super.visitSize(stackSize, localSize);
				this.stackSize = stackSize;
				this.hasLdc = false;
			}

			@Override
			public void field(byte code, String owner, String name, String type) {
				if (!hook(owner, name, type))
					super.field(code, owner, name, type);
			}

			@Override
			public void invoke(byte code, String owner, String name, String param, boolean isInterfaceMethod) {
				if (!hook(owner, name, param))
					super.invoke(code, owner, name, param, isInterfaceMethod);
			}

			private boolean hook(String owner, String name, String type) {
				d.owner = owner;
				d.name = name;
				d.rawDesc = type;
				MemberDescriptor to = hooks.get(d);
				if (to != null) {
					if ((to.modifier & 2) != 0) {
						super.ldc(new CstClass(self));
						hasLdc = true;
					}
					super.invoke(Opcodes.INVOKESTATIC, to.owner, to.name, to.rawDesc, false);

					d.modifier = 1;
					return (to.modifier & 4) == 0;
				}
				return false;
			}

			@Override
			public void visitExceptions() {
				if (hasLdc) visitSizeMax(stackSize+1, 0);
				super.visitExceptions();
			}
		});

		return d.modifier != 0;
	}
}