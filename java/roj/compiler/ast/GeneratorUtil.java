package roj.compiler.ast;

import roj.asm.MethodNode;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.insn.AttrCodeWriter;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.config.ParseException;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/11/17 19:02
 */
public class GeneratorUtil {
	public static final String RETURNSTACK_TYPE = "roj/compiler/runtime/ReturnStack";

	public static ParseTask Generator(CompileUnit file, MethodNode mn, List<String> argNames) throws ParseException {
		var impl = file.newAnonymousClass_NoBody(mn, null);
		impl.parent("roj/compiler/runtime/Generator");

		var wr = file.lc().lexer;
		int linePos = wr.LN;
		int lineIdx = wr.LNIndex;
		int pos = wr.skipBrace();

		return new ParseTask() {
			@Override
			public int priority() {return 2;}
			@Override
			public void parse(LocalContext ctx) throws ParseException {
				var file = ctx.file;
				int base = (mn.modifier&ACC_STATIC) != 0 ? 0 : 1;
				if (base != 0) mn.parameters().add(0, Type.klass(file.name()));

				String initArg = mn.rawDesc();

				initArg = initArg.substring(0, initArg.lastIndexOf(')')+1) + "V";


				var implInit = impl.newMethod(ACC_PUBLIC, "<init>", initArg);
				implInit.visitSize(2, TypeHelper.paramSize(initArg)+1);
				implInit.insn(ALOAD_0);
				implInit.invokeD(impl.parent(), "<init>", "()V");

				implInit.insn(ALOAD_0);
				implInit.field(GETFIELD, "roj/compiler/runtime/Generator", "stack", "L"+RETURNSTACK_TYPE+";");
				implInit.invokeV(RETURNSTACK_TYPE, "forWrite", "()L"+RETURNSTACK_TYPE+";");
				implInit.ldc(0);
				implInit.invokeV(RETURNSTACK_TYPE, "put", "(I)L"+RETURNSTACK_TYPE+";");

				int slot = 1;
				for (Type parameter : mn.parameters()) {
					implInit.varLoad(parameter, slot);
					slot += parameter.length();

					var varType = parameter.getActualType();
					implInit.invokeV(RETURNSTACK_TYPE, "put", "("+(varType=='L'?"Ljava/lang/Object;":(char)varType)+")L"+RETURNSTACK_TYPE+";");
				}

				implInit.insn(RETURN);
				implInit.finish();

				var attr = new AttrCodeWriter(file.cp, mn);
				mn.addAttribute(attr);
				var c = attr.cw;
				c.clazz(NEW, implInit.mn.owner);
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
				if (base != 0) {
					mn.parameters().remove(0);
					ctx.thisSlot = 1; // ALOAD_1
				}

				var cw = ctx.bp.parseGeneratorMethod(file, mn, argNames, impl, newMethod);
				cw.finish();
				newMethod.addAttribute(new UnparsedAttribute("Code", cw.bw.toByteArray()));
			}
		};
	}
}
