package roj.net.rpc;

import roj.asm.type.IType;
import roj.util.OperationDone;

import java.util.List;

/**
 * @author Roj234
 * @since 2025/10/16 10:27
 */
final class MethodStub implements Cloneable {
	String className;
	String methodName;
	List<IType> argumentTypes;
	IType returnType;

	int methodId;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MethodStub methodStub = (MethodStub) o;
		return className.equals(methodStub.className) && methodName.equals(methodStub.methodName) && argumentTypes.equals(methodStub.argumentTypes) && returnType.equals(methodStub.returnType);
	}

	@Override
	public int hashCode() {
		int result = className.hashCode();
		result = 31 * result + methodName.hashCode();
		result = 31 * result + argumentTypes.hashCode();
		result = 31 * result + returnType.hashCode();
		return result;
	}

	@Override
	protected MethodStub clone() {
		try {
			return (MethodStub) super.clone();
		} catch (CloneNotSupportedException e) {
			throw OperationDone.NEVER;
		}
	}
}
