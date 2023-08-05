package roj.launcher;

import roj.asm.Opcodes;
import roj.asm.cst.CstClass;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.mapper.util.Desc;

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
public abstract class MethodHook extends NonReentrant implements ITransformer {
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.METHOD)
	protected @interface RealDesc { String[] value(); boolean callFrom() default false; }

	//protected Map<String, List<String>> inheritMap = Collections.emptyMap();
	protected final MyHashMap<Desc, Desc> methodMap = new MyHashMap<>();

	public MethodHook() { super(null); addDefaultHook(); }
	protected void addDefaultHook() {
		for (Method m : getClass().getDeclaredMethods()) {
			if (m.getName().startsWith("hook_")) {
				int flag = AccessFlag.STATIC | AccessFlag.PUBLIC;
				if ((m.getModifiers()&flag) != flag) throw new IllegalArgumentException("hook方法必须公开静态:"+m);

				Desc toDesc = new Desc();
				toDesc.owner = this.getClass().getName().replace('.', '/');
				toDesc.name = m.getName();
				toDesc.param = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
				if (m.getName().startsWith("hook_static_")) toDesc.flags = 1;
				else toDesc.flags = 0;

				Function<Desc, List<Object>> fn = (x) -> {
					List<Object> list = new SimpleList<>();
					list.add(toDesc);
					return list;
				};

				RealDesc altDesc = m.getAnnotation(RealDesc.class);
				if (altDesc != null) {
					if (altDesc.callFrom()) toDesc.flags |= 2;
					for (String s : altDesc.value()) {
						Desc key = Desc.fromJavapLike(s);
						methodMap.put(key, toDesc);
					}
				} else {
					if (toDesc.flags == 0) {
						List<Type> param = TypeHelper.parseMethod(toDesc.param);
						String owner = param.remove(0).owner();

						Desc key = toDesc.copy();
						key.owner = owner;
						key.name = key.name.substring(5);
						key.param = TypeHelper.getMethod(param);

						methodMap.put(key, toDesc);
					} else {
						throw new IllegalArgumentException("无法获取静态注入类,请使用@RealDesc:"+m);
					}
				}
			}
		}
	}

	@Override
	public boolean transformNonReentrant(String mappedName, Context ctx) {
		String self = ctx.getData().name;
		Desc d = new Desc();

		ctx.getData().forEachCode(new CodeWriter() {
			@Override
			public void invoke(byte code, String owner, String name, String param, boolean isInterfaceMethod) {
				d.owner = "";
				d.name = name;
				d.param = param;
				Desc to = methodMap.get(d);
				if (to != null) {
					//List<String> parents = (to.flags&1) != 0 ? Collections.emptyList() : inheritMap.getOrDefault(owner, Collections.emptyList());

					if ((to.flags&2) != 0) super.ldc(new CstClass(self));
					super.invoke(Opcodes.INVOKESTATIC, to.owner, to.name, to.param, false);

					d.flags = 1;
				}

				super.invoke(code, owner, name, param, isInterfaceMethod);
			}
		});

		return d.flags != 0;
	}
}
