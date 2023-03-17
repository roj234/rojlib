package roj.kscript.parser.ast;

import roj.asm.Opcodes;
import roj.asm.tree.insn.JumpInsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.kscript.asm.CompileContext;
import roj.kscript.asm.IfNode;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.LabelNode;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 三元运算符 ? :
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class TripleIf implements Expression {
	Expression determine, truly, fake;

	public TripleIf(Expression determine, Expression truly, Expression fake) {
		this.determine = determine;
		this.truly = truly;
		this.fake = fake;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		LabelNode ifFalse = new LabelNode();
		LabelNode end = new LabelNode();

		determine.write(tree, false);
		truly.write(tree.If(ifFalse, IfNode.TRUE).Goto(end), noRet);
		fake.write(tree.Node(ifFalse), noRet);
		tree.node0(end);

		/**
		 * if(!determine)
		 *   goto :ifFalse
		 *  truly
		 *  goto :end
		 * :ifFalse
		 *  fake
		 * :end
		 */
	}

	@Override
	public KType compute(Map<String, KType> param) {
		return determine.compute(param).asBool() ? truly.compute(param) : fake.compute(param);
	}

	@Nonnull
	@Override
	public Expression compress() {
		truly = truly.compress();
		fake = fake.compress();
		if ((determine = determine.compress()).type() == -1) {
			return this;
		} else {
			return determine.asCst().asBool() ? truly : fake;
		}
	}

	@Override
	public byte type() {
		byte typeA = truly.type();
		byte typeB = fake.type();
		return typeA == typeB ? typeA : -1;
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof TripleIf)) return false;
		TripleIf tripleIf = (TripleIf) left;
		return tripleIf.determine.isEqual(determine) && tripleIf.truly.isEqual(truly) && tripleIf.fake.isEqual(fake);
	}

	@Override
	public void toVMCode(CompileContext ctx, boolean noRet) {
		determine.toVMCode(ctx, false);
		ctx.list.add(NodeCache.a_asBool_0());
		JumpInsnNode _if_ = new JumpInsnNode(Opcodes.IFEQ, null);
		ctx.list.add(_if_);
		truly.toVMCode(ctx, noRet);
		ctx.list.add(_if_.target = new LabelInsnNode());
		fake.toVMCode(ctx, noRet);
	}

	@Override
	public String toString() {
		return determine.toString() + " ? " + truly + " : " + fake;
	}
}
