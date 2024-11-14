package roj.compiler.ast;

import roj.asm.Opcodes;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.LocalVariable;
import roj.compiler.context.CompileUnit;
import roj.io.IOUtil;

import java.util.Arrays;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/11/17 0017 19:02
 */
public class EnumUtil {
	static void writeAutoConstructor(CompileUnit file, MethodWriter cw) {
		var buf = IOUtil.getSharedByteBuf()
			.put(Opcodes.ALOAD_0)
			.put(Opcodes.ALOAD_1)
			.put(Opcodes.ILOAD_2)
			.put(Opcodes.INVOKESPECIAL)
			.putShort(file.cp.getMethodRefId("java/lang/Enum", "<init>", "(Ljava/lang/String;I)V"));
		cw.insertBefore(buf);
		cw.visitSizeMax(3, 0);
	}

	public static final Type TYPE_STRING = new Type("java/lang/String");
	private static final List<LocalVariable> _NAME_ORDINAL;
	static {
		var v1 = new Variable("@name", TYPE_STRING);
		v1.slot = 1;
		v1.hasValue = true;

		var v2 = new Variable("@ordinal", Type.std(Type.INT));
		v2.slot = 2;
		v2.hasValue = true;

		_NAME_ORDINAL = Arrays.asList(new LocalVariable(v1), new LocalVariable(v2));
	}
	public static List<ExprNode> prependEnumConstructor(List<ExprNode> nodes) {
		try {
			nodes.addAll(0, _NAME_ORDINAL);
			return nodes;
		} catch (UnsupportedOperationException e) {
			var ref = new SimpleList<ExprNode>(nodes.size()+2);
			ref.addAll(_NAME_ORDINAL);
			ref.addAll(nodes);
			return ref;
		}
	}
}
