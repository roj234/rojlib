package roj.compiler.plugins.eval;

import roj.asm.*;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.insn.SwitchBlock;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.AnnotatedElement;
import roj.ci.annotation.IndirectReference;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.compiler.JavaCompileUnit;
import roj.compiler.JavaTokenizer;
import roj.compiler.api.Compiler;
import roj.compiler.api.CompilerPlugin;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.expr.NaE;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.io.IOUtil;
import roj.reflect.Reflection;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/5/30 3:47
 */
@CompilerPlugin(name = "evaluator", desc = "预编译和宏")
public interface Evaluator {
	@IndirectReference
	// timeout hook, WIP
	static boolean forceStop() {return false;}

	public static void pluginInit(Compiler ctx) {
		HashMap<String, byte[]> data = new HashMap<>();
		ArrayList<Member> invoker = new ArrayList<>();

		for (AnnotatedElement element : ctx.getClasspathAnnotations().annotatedBy("roj/compiler/plugins/eval/Constexpr")) {
			if (element.isLeaf()) {
				if (element.desc().indexOf('(') < 0) continue;

				ClassView.MOF node = (ClassView.MOF) element.node();
				node.modifier |= (char) (node.ownerNode().modifier&ACC_INTERFACE);
				invoker.add(node);
			}

			var info = ctx.resolve(element.owner());
			if (!data.containsKey(element.owner()) && info != null) {
				DynByteBuf bytes = info.toByteArray(IOUtil.getSharedByteBuf());
				data.put(element.owner(), bytes.toByteArray());
			}
		}

		for (MethodNode method : ctx.resolve("java/lang/String").methods()) {
			if ((method.modifier&(ACC_PUBLIC|ACC_STATIC)) == ACC_PUBLIC && (method.rawDesc().endsWith(")Ljava/lang/String;") || method.rawDesc().endsWith(")C"))) {
				invoker.add(method);
			}
		}

		ctx.addSandboxWhitelist("roj.compiler.plugins.eval", true);

		var invokerInst = new ClassNode();
		invokerInst.name("roj/compiler/plugins/eval/Evaluator$"+Reflection.uniqueId());
		invokerInst.addInterface("roj/compiler/plugins/eval/Evaluator");
		CodeWriter c = invokerInst.newMethod(ACC_PUBLIC, "eval", "(I[Ljava/lang/Object;)Ljava/lang/Object;");
		c.visitSize(1, 3);
		c.insn(ALOAD_2);
		c.insn(ASTORE_0);
		c.insn(ILOAD_1);
		var segment = SwitchBlock.ofSwitch(TABLESWITCH);
		c.addSegment(segment);

		for (int i = 0; i < invoker.size(); i++) {
			Label label = c.label();
			segment.def = label;
			segment.branch(i, label);

			Member mof = invoker.get(i);
			String desc = mof.rawDesc();

			c.visitSizeMax(TypeHelper.paramSize(desc) + 2, 0);

			List<Type> types = Type.getMethodTypes(desc);
			Type retVal = types.remove(types.size() - 1);

			boolean itf = (mof.modifier() & ACC_INTERFACE) != 0;
			boolean isStatic = (mof.modifier() & ACC_STATIC) != 0;

			if (!isStatic) types.add(0, Type.klass(mof.owner()));

			for (int j = 0; j < types.size(); j++) {
				c.vars(ALOAD, 2);
				c.ldc(j);
				c.insn(AALOAD);

				Type klass = types.get(j);
				if (klass.isPrimitive()) {
					c.clazz(CHECKCAST, "roj/config/node/ConfigValue");
					String converter;
					char type = (char) klass.type;
					switch (klass.type) {
						case Type.LONG -> converter = "asLong";
						case Type.FLOAT -> converter = "asFloat";
						case Type.DOUBLE -> converter = "asDouble";
						default -> {
							converter = "asInt";
							type = 'I';
						}
					}
					c.invoke(INVOKEVIRTUAL, "roj/config/node/ConfigValue", converter, "()"+type);
				} else {
					c.clazz(CHECKCAST, klass.getActualClass());
				}
			}

			c.invoke(isStatic ? INVOKESTATIC : itf ? INVOKEINTERFACE : INVOKEVIRTUAL, mof.owner(), mof.name(), desc, itf);

			Type wrapper = TypeCast.getWrapper(retVal);
			myBlock:
			if (wrapper != null) {
				char type = (char) retVal.type;
				switch (type) {
					case Type.BOOLEAN -> {
						c.invoke(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;");
						break myBlock;
					}
					case Type.LONG, Type.FLOAT, Type.DOUBLE -> {}
					default -> type = 'I';
				}
				c.invoke(INVOKESTATIC, "roj/config/node/ConfigValue", "valueOf", "("+type+")Lroj/config/node/ConfigValue;");
			} else if (retVal.owner.equals("java/lang/Class")) {
				c.clazz(NEW, "roj/asm/cp/CstClass");
				c.insn(DUP);
				c.invoke(INVOKESPECIAL, "roj/asm/cp/CstClass", "<init>", "(Ljava/lang/String;)V");
			}

			c.insn(ARETURN);
		}

		var evaluator = (Evaluator) ctx.createSandboxInstance(invokerInst);

		for (int j = 0; j < invoker.size(); j++) {
			Member mof = invoker.get(j);
			ClassDefinition info = ctx.resolve(mof.owner());
			int i = info.getMethod(mof.name(), mof.rawDesc());
			info.methods().get(i).addAttribute(new CompiledMethod(evaluator, j, Type.getReturnType(mof.rawDesc())));
		}

		ctx.newExprOp("@LavaMacro", (ctx1) -> {
			var lexer = ctx1.lexer;

			var def = new JavaCompileUnit(ctx1.file.getSourceFile() + " <macro#"+lexer.index+">", lexer.getText().toString());
			def.name("java/lang/Thread"); // just a hack..
			def.addInterface("roj/compiler/plugins/eval/Macro");

			var toString = new MethodNode(ACC_PUBLIC, def.name(), "toString", "(Lroj/text/CharList;)V");
			def.methods.add(toString);

			var lc = ctx1.compiler.createContext();
			lc.setClass(def);

			var text = lexer.getText();
			int before = lexer.prevIndex;

			var sb = new CharList().append(text, 0, before).append(';');
			try {
				lc.lexer.index = lexer.index;
				lc.lexer.LN = lexer.LN;
				lc.lexer.LNIndex = lexer.LNIndex;
				lc.lexer.state = JavaTokenizer.STATE_EXPR;
				lc.lexer.except(JavaTokenizer.lBrace);

				ParseTask.method(def, toString, Collections.singletonList("code")).parse(lc);
				def.name("roj/compiler/plugins/eval/Macro$"+Reflection.uniqueId());
				int after = lc.lexer.index;

				if (!lc.compiler.hasError) {
					var impl = (Macro) ctx.createSandboxInstance(def);
					impl.toString(sb);
				}

				// inherited (see CompileUnit#newAnonymousClass)
				lexer.setText(sb.append(text, after, text.length()).toStringAndFree(), before);
			} catch (Throwable e) {
				ctx1.report(Kind.ERROR, "plugins.eval.macro.error", e);
				e.printStackTrace();
			}

			return NaE.NOEXPR;
		});
	}

	Object eval(int methodId, Object... args);
}