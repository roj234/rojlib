package roj.asmx;

import roj.asm.*;
import roj.asm.attr.Attribute;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.Constant;
import roj.asm.frame.FrameVisitor;
import roj.asm.insn.*;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.HashSet;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2024/1/6 21:40
 */
public class TransformUtil {
	// region apiOnly
	/**
	 * 删除方法的代码，可用于生成api-only package
	 */
	public static boolean apiOnly(ClassNode data) {
		boolean flag = false;
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode ms = methods.get(i);

			if (ms.getAttribute("Code") != null) {
				apiOnly(data, ms);
				flag = true;
			}
		}
		return flag;
	}
	public static void apiOnly(ClassNode data, MethodNode mn) {
		CodeWriter cw = new CodeWriter();
		cw.init(new ByteList(16), data.cp, mn);

		Type t = mn.returnType();

		cw.visitSize(t.length(), TypeHelper.paramSize(mn.rawDesc()) + ((ACC_STATIC & mn.modifier()) == 0 ? 1 : 0));

		if (mn.name().equals("<init>")) {
			cw.visitSizeMax(1, 0);
			cw.insn(ALOAD_0);
			cw.invokeD(data.parent(), "<init>", "()V");
		}

		switch (t.getActualType()) {
			//case VOID -> {}
			case OBJECT -> cw.insn(ACONST_NULL);
			case BOOLEAN, BYTE, CHAR, SHORT, INT -> cw.insn(ICONST_0);
			case FLOAT -> cw.insn(FCONST_0);
			case DOUBLE -> cw.insn(DCONST_0);
			case LONG -> cw.insn(LCONST_0);
		}
		//noinspection MagicConstant
		cw.insn(t.getOpcode(IRETURN));
		cw.finish();

		mn.addAttribute(new UnparsedAttribute("Code", cw.bw));
	}
	// endregion
	// region Access Transformer
	/**
	 * 修改data中toOpen所包含的访问权限至public
	 */
	public static void makeAccessible(ClassDefinition data, Collection<String> toOpen) {
		if (toOpen.contains("<$extend>")) {
			data.modifier(toPublic(data.modifier(), true));
		}

		if (data instanceof ClassNode cdata) {
			var classes = cdata.getInnerClasses();
			for (int i = 0; i < classes.size(); i++) {
				var clz = classes.get(i);
				if (toOpen.contains(clz.self)) {
					clz.modifier = (char) toPublic(clz.modifier, true);
				}
			}
		}

		boolean starP = true;
		if (toOpen.contains("*")) {
			toOpen = null;
			starP = false;
		} else if (toOpen.contains("*P")) {
			toOpen = null;
		}

		toPublic(toOpen, starP, data.fields());
		toPublic(toOpen, starP, data.methods());
	}
	private static void toPublic(Collection<String> toOpen, boolean starP, List<? extends Member> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			Member node = nodes.get(i);
			if (toOpen != null && !toOpen.contains(node.name()) && !toOpen.contains(node.name()+node.rawDesc())) continue;

			int flag = toPublic(node.modifier(), starP || toOpen != null);
			node.modifier(flag);
		}
	}
	private static int toPublic(int flag, boolean starP) {
		flag &= ~(ACC_PRIVATE|ACC_FINAL);
		if (starP || (flag& ACC_PROTECTED) == 0)
			return (flag & ~ACC_PROTECTED) | ACC_PUBLIC;
		return flag;
	}
	// endregion
	public static void compress(ClassNode data) {
		var lazyLDC = new HashSet<Constant>();
		var cpw = AsmCache.getInstance().constPool();
		// 1 byte per ldc instruction, slightly decrease output size
		var ldcFirst = new CodeVisitor() {
			protected void ldc(byte code, Constant c) {
				if (code != LDC2_W) {
					if (lazyLDC.add(c)) {
						cpw.add(c);
					}
				}
			}
		};

		var methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			// serialize Code attribute to byte[] for further visiting
			methods.get(i).getAttribute(data.cp, "Code");
		}
		for (int i = 0; i < methods.size(); i++) {
			// do visit
			var code = methods.get(i).getAttribute(data.cp, "Code");
			if (code != null) ldcFirst.visit(data.cp, code.getRawData().slice());
		}
		// add internal reference, e.g. Utf8 in String constant
		for (var constant : lazyLDC) cpw.intern(constant);

		ByteList tmp = new ByteList();
		CodeWriter cw = new CodeWriter();
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.getAttribute("Code");
			if (code != null) {
				tmp.clear();
				cw.init(tmp, cpw, mn);
				cw.visit(data.cp, code.getRawData());
				cw.finish();

				byte[] array = tmp.toByteArray();
				// don't parse my 'Code' in next parsed() call
				mn.addAttribute(new Attribute() {
					@Override public String name() {return "Code";}
					@Override public DynByteBuf getRawData() {return ByteList.wrap(array);}
					@Override public String toString() {return "DontParseCode";}
				});
			}
		}
		tmp.release();
		// handle other attributes
		data.parsed();
		// swap shared constant pool
		AsmCache.getInstance().constPool(data.cp);
		data.cp = cpw;

		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.getAttribute("Code");
			// convert to default impl
			if (code != null) mn.addAttribute(new UnparsedAttribute("Code", code.getRawData()));
		}
	}

	public static void prependStaticConstructor(ClassNode data, Consumer<AbstractCodeWriter> writer) {
		MethodNode clinit = data.getMethodObj("<clinit>");
		AbstractCodeWriter c;
		if (clinit == null) {
			CodeWriter c1 = data.newMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "<clinit>", "()V");
			c1.visitSize(1, 0);
			c1.computeFrames(FrameVisitor.COMPUTE_FRAMES| FrameVisitor.COMPUTE_SIZES|CodeWriter.ADD_RETURN);
			c = c1;
		} else {
			if (clinit.getAttribute("Code") instanceof AttrCodeWriter cw) {
				c = cw.cw;
				clinit = null;
			} else {
				c = new InsnList();
			}
		}

		writer.accept(c);

		if (clinit != null) {
			Code code = clinit.getAttribute(data.cp, Attribute.Code);
			if (code.stackSize == 0) code.stackSize = 1;
			code.computeFrames(FrameVisitor.COMPUTE_FRAMES| FrameVisitor.COMPUTE_SIZES);
			code.instructions.replace(0,0, (InsnList) c, false);
		}
	}

	/**
	 * 在类节点上创建代理实现结构
	 * 你觉得这是AOP那这就是AOP，我觉得{@link roj.asmx.injector.CodeWeaver}也是AOP
	 *
	 * @param node 目标类节点（将被修改）
	 * @param types 要实现的类数组，第一项要么是接口，要么具有至少protected的无参构造器，其余项必须是接口
	 * @param overrider 方法重写器，返回true表示已处理该方法
	 * @param extraFieldIds 额外字段的字段ID，这些字段需要通过apply传递参数
	 *
	 * <h3>参数数组约定：</h3>
	 * <pre>{@code
	 * Object[] params = {
	 *   proxyTarget,    // 对应$proxy字段
	 *   extraField1,    // 对应第一个额外字段
	 *   extraField2     // 对应第二个额外字段
	 * };}</pre>
	 * @since 2023/5/17 9:57
	 */
	public static void proxyClass(ClassNode node, Class<?>[] types, BiFunction<Method, CodeWriter, Boolean> overrider, int... extraFieldIds) {
		for (int i = 1; i < types.length; i++) {
			if (!types[i].isInterface()) throw new IllegalArgumentException("not interface");
		}

		String instanceType = types[0].getName().replace('.', '/');
		if (types[0].isInterface()) {
			node.addInterface(instanceType);
		} else {
			node.parent(instanceType);
		}

		node.defaultConstructor();
		node.addInterface("java/util/function/Function");

		int delegation = node.newField(0, "$proxy", klass(instanceType));

		// apply (newInstance)
		var c = node.newMethod(ACC_PUBLIC | ACC_FINAL, "apply", "(Ljava/lang/Object;)Ljava/lang/Object;");
		c.visitSize(3, 2);

		c.insn(ALOAD_1);
		c.clazz(CHECKCAST, "[Ljava/lang/Object;");
		c.insn(ASTORE_1);

		c.clazz(NEW, node.name());
		c.insn(DUP);
		c.invokeD(node.name(), "<init>", "()V");
		c.insn(ASTORE_0);

		c.insn(ALOAD_0);
		c.unpackArray(1, 0, node.fields.get(delegation).fieldType());
		c.field(PUTFIELD, node, delegation);

		for (int i = 0; i < extraFieldIds.length; i++) {
			int id = extraFieldIds[i];
			c.insn(ALOAD_0);
			c.unpackArray(1, i+1, node.fields.get(id).fieldType());
			c.field(PUTFIELD, node, id);
		}

		c.insn(ALOAD_0);
		c.insn(ARETURN);
		//end apply

		for (Class<?> itf : types) {
			for (Method method : itf.getMethods()) {
				String desc = TypeHelper.class2asm(method.getParameterTypes(), method.getReturnType());
				if (node.getMethod(method.getName(), desc) >= 0) continue;

				var cw = node.newMethod(ACC_PUBLIC | ACC_FINAL, method.getName(), desc);
				int slots = TypeHelper.paramSize(desc)+1;
				cw.visitSize(slots,slots);
				cw.insn(ALOAD_0);
				cw.field(GETFIELD, node, delegation);

				List<Type> parameters = cw.method.parameters();
				for (int i = 0; i < parameters.size(); i++) {
					cw.varLoad(parameters.get(i), i+1);
				}

				if (!overrider.apply(method, cw)) {
					cw.invokeItf(instanceType, cw.method.name(), desc);
				}
				cw.return_(cw.method.returnType());
				cw.finish();
			}
		}
	}
}