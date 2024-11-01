package roj.compiler.resolve;

import roj.asm.type.IType;

/**
 * @author solo6975
 * @since 2020/12/31 18:44
 */
final class UnableCastException extends ResolveException {
	final IType from, to;
	final TypeCast.Cast code;

	UnableCastException(IType from, IType to, TypeCast.Cast cast) {
		super("_should_be_caught_", null, false, false);
		this.from = from;
		this.to = to;
		this.code = cast;
	}
}