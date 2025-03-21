package roj.compiler.resolve;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.concurrent.Flow;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/2/7 0007 4:59
 */
public final class MethodResult {
	public MethodNode method;
	public boolean directVarargCall;
	@Nullable
	public IType[] desc, exception;

	public IntMap<Object> namedParams;

	public int distance;
	public Object[] error;

	public MethodResult(MethodNode mn, int distance, boolean dvc) {
		this.method = mn;
		this.distance = distance;
		this.directVarargCall = dvc;
	}
	public MethodResult(int errorCode, Object... error) {
		this.distance = errorCode;
		this.error = error;
	}

	public @NotNull List<IType> parameters() {
		if (desc == null) return Helpers.cast(method.parameters());
		// 不复制数组
		var list = SimpleList.asModifiableList(desc); list.pop();
		return list;
	}
	public @NotNull IType returnType() {return desc == null ? method.returnType() : desc[desc.length-1];}

	public List<IType> getExceptions(LocalContext ctx) {
		if (exception != null) return Arrays.asList(exception);
		else {
			var list = method.getAttribute(ctx.classes.getClassInfo(method.owner).cp(), Attribute.Exceptions);
			if (list == null) return Collections.emptyList();
			return Flow.of(list.value).map((Function<String, IType>) Type::klass).toList();
		}
	}

	public void addExceptions(LocalContext ctx, boolean twr) {
		for (IType ex : getExceptions(ctx)) {
			if (twr && ex.owner().equals("java/lang/InterruptedException"))
				ctx.report(Kind.WARNING, "block.try.interrupted");

			ctx.addException(ex);
		}
	}

	public static void writeInvoke(MethodNode method, LocalContext ctx, MethodWriter cw) {
		byte opcode;
		if ((ctx.classes.getClassInfo(method.owner).modifier & ACC_INTERFACE) != 0) {
			opcode = INVOKEINTERFACE;
		} else if ((method.modifier & ACC_STATIC) != 0) {
			opcode = INVOKESTATIC;
		} else if ((method.modifier & ACC_PRIVATE) != 0 && method.owner.equals(ctx.file.name())) {
			opcode = INVOKESPECIAL;
		} else {
			opcode = INVOKEVIRTUAL;
		}

		cw.invoke(opcode, method);
	}
}