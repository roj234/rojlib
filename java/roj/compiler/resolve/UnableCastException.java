package roj.compiler.resolve;

import roj.asm.type.IType;

/**
 * @author solo6975
 * @since 2020/12/31 18:44
 */
public class UnableCastException extends ResolveException {
	public final IType from, to;
	public TypeCast.Cast code;

	public UnableCastException(IType from, IType to, TypeCast.Cast cast) {
		super(from+cast.getCastDesc()+to, null, true, true);
		this.from = from;
		this.to = to;
		this.code = cast;
	}

	@Override
	public Throwable fillInStackTrace() {return this;}
}