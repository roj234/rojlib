package roj.compiler.resolve;

import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrClassList;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.IntMap;
import roj.compiler.context.CompileContext;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/2/7 0007 4:59
 */
public final class MethodResult {
	public MethodNode method;
	public boolean directVarargCall;
	public IType[] desc, exception;

	public IntMap<Object> namedParams;

	public int distance;
	public Object[] error;

	public MethodResult(MethodNode mn) {
		this.method = mn;
		// 0 and false
	}
	public MethodResult(MethodNode mn, int distance, boolean dvc) {
		this.method = mn;
		this.distance = distance;
		this.directVarargCall = dvc;
	}
	public MethodResult(int errorCode, Object... error) {
		this.distance = errorCode;
		this.error = error;
	}

	public void addExceptions(CompileContext ctx, IClass cn, int issuer) {
		if (exception != null) {
			for (IType ex : exception) {
				ctx.addException(ex);
			}
		} else {
			AttrClassList exAttr = method.parsedAttr(cn.cp(), Attribute.Exceptions);
			if (exAttr != null) {
				List<String> value = exAttr.value;
				for (int i = 0; i < value.size(); i++)
					ctx.addException(new Type(value.get(i)));
			}
		}
	}
}