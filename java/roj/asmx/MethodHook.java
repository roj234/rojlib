package roj.asmx;

import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
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
 * @since 2023/8/4 0004 15:36
 */
public abstract class MethodHook implements ITransformer {
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected @interface RealDesc { String[] value(); boolean callFrom() default false; }

	protected final MyHashMap<Desc, Desc> hooks = new MyHashMap<>();

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

				Desc toDesc = new Desc();
				toDesc.owner = this.getClass().getName().replace('.', '/');
				toDesc.name = m.getName();
				toDesc.param = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
				int i1 = sb.indexOf("static_");
				toDesc.modifier = (char) (i1>=0&&i1<=i ? 1 : 0);

				Function<Desc, List<Object>> fn = (x) -> {
					List<Object> list = new SimpleList<>();
					list.add(toDesc);
					return list;
				};

				i1 = sb.indexOf("newInstance_");
				if (i1>=0&&i1<=i) toDesc.modifier |= 4;

				RealDesc altDesc = m.getAnnotation(RealDesc.class);
				if (altDesc != null) {
					if (altDesc.callFrom()) toDesc.modifier |= 2;
					for (String s : altDesc.value()) {
						Desc key = Desc.fromJavapLike(s);
						hooks.put(key, toDesc);
					}
				} else {
					if (toDesc.modifier == 0) {
						List<Type> param = Type.methodDesc(toDesc.param);
						String owner = param.remove(0).owner();

						i1 = sb.indexOf("callFrom_");
						if (i1>=0&&i1<=i) {
							param.remove(param.size()-2);
							toDesc.modifier |= 2;
						}

						Desc key = toDesc.copy();
						key.owner = owner;
						key.name = sb.substring(i, sb.length());
						key.param = Type.toMethodDesc(param);

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
		Desc d = new Desc();

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
				d.param = type;
				Desc to = hooks.get(d);
				if (to != null) {
					if ((to.modifier & 2) != 0) {
						super.ldc(new CstClass(self));
						hasLdc = true;
					}
					super.invoke(Opcodes.INVOKESTATIC, to.owner, to.name, to.param, false);

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