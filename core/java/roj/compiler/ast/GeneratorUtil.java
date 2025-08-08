package roj.compiler.ast;

import roj.asm.MethodNode;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.config.ParseException;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/11/17 19:02
 */
public class GeneratorUtil {
	public static final String RETURNSTACK_TYPE = "roj/compiler/runtime/ReturnStack";
	public static final String GENERATOR_TYPE = "roj/compiler/runtime/Generator";

	public static ParseTask Generator(CompileUnit file, MethodNode mn, List<String> argNames) throws ParseException {
		var impl = file.newAnonymousClass_NoBody(mn, null);
		impl.parent(GENERATOR_TYPE);

		var wr = file.lc().lexer;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		int pos = wr.skipBrace();

		return new ParseTask() {
			@Override
			public int priority() {return 2;}
			@Override
			public void parse(CompileContext ctx) throws ParseException {
				var file = ctx.file;
				int base = (mn.modifier&ACC_STATIC) != 0 ? 0 : 1;
				if (base != 0) mn.parameters().add(0, Type.klass(file.name()));

				String initArg = mn.rawDesc();

				initArg = initArg.substring(0, initArg.lastIndexOf(')')+1) + "V";


				var implInit = impl.newMethod(ACC_PUBLIC, "<init>", initArg);
				implInit.visitSize(2, TypeHelper.paramSize(initArg)+1);
				implInit.insn(ALOAD_0);
				implInit.invokeD(impl.parent(), "<init>", "()V");

				if (base != 0) {
					impl.newField(ACC_PRIVATE|ACC_FINAL, "$", "L"+file.name()+";");
					implInit.insn(ALOAD_0);
					implInit.insn(ALOAD_1);
					implInit.field(PUTFIELD, impl, 0);
				}

				implInit.insn(ALOAD_0);
				implInit.field(GETFIELD, GENERATOR_TYPE, "stack", "L"+RETURNSTACK_TYPE+";");
				implInit.invokeV(RETURNSTACK_TYPE, "forWrite", "()L"+RETURNSTACK_TYPE+";");
				implInit.ldc(0);
				implInit.invokeV(RETURNSTACK_TYPE, "put", "(I)L"+RETURNSTACK_TYPE+";");

				int slot = 1+base;
				List<Type> parameters = mn.parameters();
				for (int i = base; i < parameters.size(); i++) {
					Type parameter = parameters.get(i);
					implInit.varLoad(parameter, slot);
					slot += parameter.length();

					var varType = parameter.getActualType();
					implInit.invokeV(RETURNSTACK_TYPE, "put", "(" + (varType == 'L' ? "Ljava/lang/Object;" : (char) varType) + ")L" + RETURNSTACK_TYPE + ";");
				}

				implInit.insn(RETURN);
				implInit.finish();

				var c = mn.overwrite(file.cp);
				c.clazz(NEW, implInit.mn.owner());
				c.insn(DUP);
				c.visitSize(TypeHelper.paramSize(implInit.mn.rawDesc())+2, TypeHelper.paramSize(mn.rawDesc()));

				List<Type> myPar = mn.parameters();
				slot = 0;
				for (int i = 0; i < myPar.size(); i++) {
					Type from = myPar.get(i);
					c.varLoad(from, slot);
					slot += from.length();
				}

				c.invoke(INVOKESPECIAL, implInit.mn);
				c.insn(ARETURN);
				c.finish();

				var newMethod = new MethodNode(ACC_PROTECTED | ACC_FINAL, impl.name(), "invoke", "(L"+RETURNSTACK_TYPE+";)V");
				impl.methods.add(newMethod);

				ctx.lexer.init(pos, linePos, lineIdx);
				if (base != 0) mn.parameters().remove(0);

				var cw = ctx.bp.parseGenerator(file, mn, argNames, impl, newMethod);
				try {
					cw.finish();
				} catch (Exception e) {
					e.printStackTrace();
				}
				newMethod.addAttribute(new UnparsedAttribute("Code", cw.bw.toByteArray()));
			}
		};
	}
}
