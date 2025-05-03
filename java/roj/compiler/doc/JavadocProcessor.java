package roj.compiler.doc;

import roj.asm.Attributed;

/**
 * @author Roj234
 * @since 2025/3/31 5:33
 */
public interface JavadocProcessor {
	JavadocProcessor NULL = node -> {};
	void attach(Attributed node);
}
