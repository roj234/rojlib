package roj.asmx.mapper.obf.string;

import roj.asm.cp.CstString;
import roj.asm.tree.MethodNode;
import roj.asm.visitor.CodeWriter;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * @author Roj234
 * @since 2023/9/17 0017 19:27
 */
public interface StringObfuscator {
	boolean requiresLineNumber();
	void obfuscate(CodeWriter method, CstString string, int lineNumber, Random rand, DeobfuscationMethodPlacer methodGenerator);

	interface DeobfuscationMethodPlacer {
		@Nullable MethodNode getMethod(String identifier);
		CodeWriter generateMethod(@Nullable String identifier);
	}
}