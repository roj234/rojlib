package roj.asm.tree.insn;

import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.SimpleList;

import java.util.List;

/**
 * 抽象，方法执行
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public abstract class IInvokeInsnNode extends InsnNode {
	public IInvokeInsnNode() {}
	public IInvokeInsnNode(byte code) {
		super(code);
	}

	public String name;
	String rawDesc;
	List<Type> params;
	Type returnType;

	final void initPar() {
		if (params == null) {
			if (rawDesc.startsWith("()")) {
				params = new SimpleList<>();
				returnType = TypeHelper.parseReturn(rawDesc);
			} else {
				params = TypeHelper.parseMethod(rawDesc);
				returnType = params.remove(params.size() - 1);
			}
		}
	}

	public final Type returnType() {
		initPar();
		return returnType;
	}

	public final List<Type> parameters() {
		initPar();
		return params;
	}

	public final String rawDesc() {
		if (params != null) {
			params.add(returnType);
			rawDesc = TypeHelper.getMethod(params, rawDesc);
			params.remove(params.size() - 1);
		}
		return rawDesc;
	}

	/**
	 * (I)Lasm/util/ByteWriter;
	 *
	 * @param param java规范中的方法参数描述符
	 */
	public final void rawDesc(String param) {
		this.rawDesc = param;
		if (params != null) {
			params.clear();
			TypeHelper.parseMethod(param, params);
			returnType = params.remove(params.size() - 1);
		}
	}

	/**
	 * asm/util/ByteWriter.putShort:(I)Lasm/util/ByteWriter;
	 *
	 * @param desc javap格式的描述符
	 */
	public abstract void fullDesc(String desc);

	public abstract String fullDesc();
}