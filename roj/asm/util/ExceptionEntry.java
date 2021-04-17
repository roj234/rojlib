package roj.asm.util;

import roj.asm.cst.CstClass;
import roj.asm.tree.insn.InsnNode;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class ExceptionEntry {
	public InsnNode start, end, handler;
	public String type;

	public static final String ANY = null;

	public ExceptionEntry() {}

	public ExceptionEntry(InsnNode start, InsnNode end, InsnNode handler, CstClass catchType) {
		// 如果catch_type项的值为零，则为所有异常调用此异常处理程序。
		// 0 => Constant.null
		this(start,end,handler,catchType == null ? ANY : catchType.name().str());
	}

	public ExceptionEntry(InsnNode start, InsnNode end, InsnNode handler, String catchType) {
		this.start = start;
		this.end = end;
		this.handler = handler;
		this.type = catchType;
	}

	@Override
	public String toString() {
		return "ExceptionEntry{" + "start=" + start + ", end=" + end + ", handler=" + handler + ", type='" + type + '\'' + '}';
	}
}
