package roj.compiler.asm;

import roj.asm.tree.MethodNode;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.compiler.asm.node.LazyIINC;
import roj.compiler.asm.node.LazyLoadStore;
import roj.compiler.context.ClassContext;
import roj.compiler.context.CompileContext;
import roj.compiler.context.CompileUnit;
import roj.util.ByteList;

import static roj.asm.Opcodes.*;

/**
 * @author Roj233
 * @since 2022/2/24 19:19
 */
public class MethodWriter extends CodeWriter {
	public CompileUnit owner;
	public ClassContext ctx;
	public CompileContext ctx1;
	public Variable current_variable;

	private SimpleList<TryCatchEntry> entryList = new SimpleList<>();

	public MethodWriter(CompileUnit unit, MethodNode mn) {
		this.owner = unit;
		this.ctx = unit.ctx();
		this.ctx1 = CompileContext.get();
		this.init(new ByteList(),unit.cp,mn,(byte)0);
	}

	public TryCatchEntry addException(Label str, Label end, Label proc, String s) {
		TryCatchEntry entry = new TryCatchEntry(str, end, proc, s);
		entryList.add(entry);
		return entry;
	}

	public void load(Variable v) { addSegment(new LazyLoadStore(v, false)); }
	public void store(Variable v) { addSegment(new LazyLoadStore(v, true)); }
	public void iinc(Variable v, int delta) { addSegment(new LazyIINC(v, delta)); }

	public boolean hasNormalEnd() {
		return false;
	}

	private final SimpleList<Label> jumpNo = new SimpleList<>();
	private final MyBitSet cond = new MyBitSet();
	private boolean canuse;
	public int beginJumpOn(boolean ifNe, Label label) {
		int size = jumpNo.size();
		jumpNo.add(label);
		cond.set(size, ifNe);
		canuse = true;
		return size;
	}

	public void endJumpOn(int size) {
		if (jumpNo.size() > size) {
			assert jumpNo.size() == size+1;

			Label target = jumpNo.pop();
			jump(cond.remove(jumpNo.size()) ? IFNE : IFEQ, target);
			canuse = true;
		}
	}

	public boolean jumpOn(int code) {
		if (!canuse) return false;
		canuse = false;

		Label target = jumpNo.remove(jumpNo.size()-1);
		boolean invert = !cond.remove(jumpNo.size());
		if (invert) {
			assert code >= IFEQ && code <= IF_acmpne;
			code = IFEQ + ((code-IFEQ) ^ 1); // 草，Opcode的排序还真的很有讲究
		}

		jump((byte) code, target);
		return true;
	}
}