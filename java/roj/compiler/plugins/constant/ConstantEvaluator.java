package roj.compiler.plugins.constant;

import roj.asm.tree.*;
import roj.asm.tree.insn.SwitchEntry;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.SwitchSegment;
import roj.asmx.AnnotatedElement;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.GlobalContextApi;
import roj.compiler.resolve.TypeCast;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/5/30 0030 3:47
 */
public interface ConstantEvaluator {
	public static void init(GlobalContextApi ctx) throws IOException {
		MyHashMap<String, byte[]> data = new MyHashMap<>();
		SimpleList<RawNode> invoker = new SimpleList<>();

		for (AnnotatedElement element : ctx.getClasspathAnnotations().annotatedBy("roj/compiler/plugins/constant/Constant")) {
			if (element.isLeaf()) {
				if (element.desc().indexOf('(') < 0) continue;

				AccessData.MOF node = (AccessData.MOF) element.node();
				node.acc |= (char) (node.owner().acc&ACC_INTERFACE);
				invoker.add(node);
			}

			var info = ctx.getClassInfo(element.owner());
			if (!data.containsKey(element.owner()) && info != null) {
				DynByteBuf bytes = info.getBytes(IOUtil.getSharedByteBuf());
				data.put(element.owner(), bytes.toByteArray());
			}
		}

		invoker.add(new MethodNode(0, "java/lang/String", "charAt", "(I)C"));
		invoker.add(new MethodNode(0, "java/lang/String", "replace", "(CC)Ljava/lang/String;"));

		if (invoker.size() > 0) {
			ctx.report(null, Kind.WARNING, -1, "lava.sandbox.enabled");
		}

		SandboxClassLoader scl = new SandboxClassLoader(ConstantEvaluator.class.getClassLoader(), data);

		ConstantData invokerInst = new ConstantData();
		invokerInst.name("roj/compiler/plugins/constant/SandboxInvoker");
		invokerInst.addInterface("roj/compiler/plugins/constant/ConstantEvaluator");
		ClassDefiner.premake(invokerInst);
		CodeWriter c = invokerInst.newMethod(ACC_PUBLIC, "eval", "(I[Ljava/lang/Object;)Ljava/lang/Object;");
		c.visitSize(1, 3);
		c.one(ALOAD_2);
		c.one(ASTORE_0);
		c.one(ILOAD_1);
		SwitchSegment segment = new SwitchSegment(TABLESWITCH);
		c.addSegment(segment);

		for (int i = 0; i < invoker.size(); i++) {
			Label label = c.label();
			segment.def = label;
			segment.targets.add(new SwitchEntry(i, label));

			RawNode mof = invoker.get(i);
			String desc = mof.rawDesc();

			c.visitSizeMax(TypeHelper.paramSize(desc) + 2, 0);

			List<Type> types = TypeHelper.parseMethod(desc);
			Type retVal = types.remove(types.size() - 1);

			boolean itf = (mof.modifier() & ACC_INTERFACE) != 0;
			boolean isStatic = (mof.modifier() & ACC_STATIC) != 0;

			if (!isStatic) types.add(0, new Type(mof.ownerClass()));

			for (int j = 0; j < types.size(); j++) {
				c.vars(ALOAD, 2);
				c.ldc(j);
				c.one(AALOAD);

				Type klass = types.get(j);
				if (klass.isPrimitive()) {
					c.clazz(CHECKCAST, "roj/asm/tree/anno/AnnVal");
					String converter = switch (klass.type) {
						case Type.LONG -> "asLong";
						case Type.FLOAT -> "asFloat";
						case Type.DOUBLE -> "asDouble";
						default -> "asInt";
					};
					c.invoke(INVOKEVIRTUAL, "roj/asm/tree/anno/AnnVal", converter, "()I");
				} else {
					c.clazz(CHECKCAST, klass.getActualClass());
				}
			}

			c.invoke(isStatic ? INVOKESTATIC : itf ? INVOKEINTERFACE : INVOKEVIRTUAL, mof.ownerClass(), mof.name(), desc, itf);

			Type wrapper = TypeCast.getWrapper(retVal);
			if (wrapper != null) {
				c.invoke(INVOKESTATIC, "roj/asm/tree/anno/AnnVal", "valueOf", "("+(char)retVal.type+")Lroj/asm/tree/anno/AnnVal;");
			} else if (retVal.owner.equals("java/lang/Class")) {
				c.clazz(NEW, "roj/asm/cp/CstClass");
				c.one(DUP);
				c.invoke(INVOKESPECIAL, "roj/asm/cp/CstClass", "<init>", "(Ljava/lang/String;)V");
			}

			c.one(ARETURN);
		}

		ConstantEvaluator evaluator = (ConstantEvaluator) ClassDefiner.make(invokerInst, scl);

		for (int j = 0; j < invoker.size(); j++) {
			RawNode mof = invoker.get(j);
			IClass info = ctx.getClassInfo(mof.ownerClass());
			int i = info.getMethod(mof.name(), mof.rawDesc());
			info.methods().get(i).putAttr(new ConstantMethod(evaluator, j, TypeHelper.parseReturn(mof.rawDesc())));
		}
	}

	Object eval(int methodId, Object... args);
}