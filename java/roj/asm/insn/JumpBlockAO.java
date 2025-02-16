package roj.asm.insn;

import roj.compiler.context.GlobalContext;
import roj.util.DynByteBuf;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2024/2/25 0:17
 */
public final class JumpBlockAO extends JumpBlock {
	private SolidBlock writeReplace;

	public JumpBlockAO(byte code, Label target) { super(code, target); }

	@Override
	@SuppressWarnings("fallthrough")
	public boolean put(CodeWriter to, int segmentId) {
		if (writeReplace != null) return writeReplace.put(to, segmentId);
		if (!target.isValid()) throw new IllegalStateException("target label is not valid: "+target);

		// 无法访问的代码 (连续goto自动合并会误判)
		if (!to.isContinuousControlFlow(segmentId-1)) {
			GlobalContext.debugLogger().warn("无法访问的代码: "+this+" (from b"+segmentId+", bci="+to.bci+") => "+target.getValue());
			doWriteReplace();
			return true;
		}

		while (target.offset == 0 && target.getBlock() > 0) {
			CodeBlock codeBlock = to.codeBlocks.get(target.getBlock());
			if (!(codeBlock instanceof JumpBlockAO j) || !j.isTerminate()) break;
			target = j.target;
		}

		// if-goto-segment自动翻转
		if (!isTerminate() &&
			target.offset == 0 && target.getBlock() == segmentId+2 &&
			!(to.codeBlocks.get(segmentId+2) instanceof JumpBlock) &&
			to.codeBlocks.get(segmentId+1) instanceof JumpBlock t &&
			t.isTerminate()) {

			target = t.target;
			code = (byte) (IFEQ + ((code-IFEQ) ^ 1));
			to.codeBlocks.set(segmentId+1, SolidBlock.EMPTY);
		}

		DynByteBuf o = to.bw;
		int off = target.getValue() - to.bci;

		fv_bci = to.bci;

		int len = 3;
		int newLen = length();

		switch (code) {
			case JSR: case JSR_W:
				if (((short) off) != off) {
					o.put(code = JSR_W).putInt(off);
					len = 5;
				} else {
					o.put(code = JSR).putShort(off);
				}
			break;
			case GOTO: case GOTO_W:
				if (((short) off) != off) {
					o.put(code = GOTO_W).putInt(off);
					len = 5;
				} else {
					o.put(code = GOTO).putShort(off);
				}
			break;
			default:
				if (((short) off) != off) {
					o.put(IFEQ + ((code-IFEQ) ^ 1)).putShort(8).put(GOTO_W).putInt(off);
					len = 8;
				} else {
					o.put(code).putShort(off);
				}
			break;
		}

		if (target.getValue() == to.bci + newLen) {
			GlobalContext.debugLogger().warn("无意义的跳转: "+this+" (from b"+segmentId+", bci="+to.bci+") => "+target.getValue());
			doWriteReplace();
			return true;
		}
		return len != newLen;
	}
	private void doWriteReplace() {writeReplace = code < GOTO ? new SolidBlock(code >= IF_icmpeq ? POP2 : POP) : SolidBlock.EMPTY;}

	@Override public final int length() { return writeReplace != null ? writeReplace.length() : super.length(); }

	@Override
	public CodeBlock move(AbstractCodeWriter to, int blockMoved, boolean clone) {
		Label rx = copyLabel(target, to, blockMoved, clone);
		return clone?new JumpBlockAO(code,rx):this;
	}
}