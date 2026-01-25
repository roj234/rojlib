package roj.compiler.types;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.compiler.ast.VariableDeclare;
import roj.util.function.Flow;

import java.util.List;

/**
 * @author Roj234
 * @since 2026/01/29 17:22
 */
public final class LambdaForm extends VirtualType {
	public static final int ARGC_UNKNOWN = 0x10000;
	public static final IType ARGC_UNKNOWN_TYPE = new LambdaForm(ARGC_UNKNOWN);

	private static final IType[] untypedCache = new IType[10];
	static {
		for (int i = 0; i < 10; i++) untypedCache[i] = new LambdaForm(i);
	}

	public static int getLambdaArgc(IType lambdaType) {
		if (lambdaType instanceof LambdaForm lf) {
			return lf.parameterCount;
		}
		return -1;
	}
	@Nullable
	public static List<IType> getLambdaArgs(IType lambdaType) {
		return ((LambdaForm) lambdaType).parameterTypes;
	}

	/**
	 * lambda类型不是给TypeCast用的！
	 * @see roj.compiler.resolve.Inferrer#cast(IType, IType)
	 */
	public static IType of(List<VariableDeclare> args) {
		var argc = args.size();
		if (argc == 0) return untypedCache[0];

		var untyped = args.get(0).type == null;
		if (untyped) return argc < 10 ? untypedCache[argc] : new LambdaForm(argc);

		return new LambdaForm(Flow.of(args).map(vd -> vd.type).toList());
	}

	private List<IType> parameterTypes;
	private final int parameterCount;

	private LambdaForm(int argc) {
		super("<lambda>");
		this.parameterCount = argc;
	}
	private LambdaForm(List<IType> args) {
		super("<lambda>");
		this.parameterTypes = args;
		this.parameterCount = args.size();
	}

	@Override
	public String getI18nKey() {return "<lambda>:["+parameterCount+"]";}
}
