package roj.kscript.asm;

import roj.collect.IntBiMap;
import roj.collect.RSegmentTree;
import roj.kscript.parser.JSLexer;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
import roj.kscript.util.VInfo;
import roj.kscript.util.Variable;

import java.util.List;

/**
 * @author Roj234
 * @since 2020/9/27 18:50
 */
public final class IfNode extends Node {
	final byte type;
	Node target;
	VInfo diff;

	public static final short TRUE = 53;

	public IfNode(short type, LabelNode target) {
		this.type = (byte) (type - 53);
		this.target = target;
	}

	@Override
	public Opcode getCode() {
		return Opcode.IF;
	}

	@Override
	protected void compile() {
		if (target.getClass() == LabelNode.class) {
			target = target.next;
			if (target instanceof VarGNode && ((VarGNode) target).name instanceof Node) {
				target = (Node) ((VarGNode) target).name;
			} else if (target instanceof IncrNode && ((IncrNode) target).name instanceof Node) {
				target = (Node) ((IncrNode) target).name;
			}
		}
	}

	@Override
	protected void genDiff(RSegmentTree<RSegmentTree.Wrap<Variable>> var, IntBiMap<Node> idx) {
		List<RSegmentTree.Wrap<Variable>> self = var.collect(idx.getInt(this)), dest = var.collect(idx.getInt(target));
		if (self != dest) {
			diff = NodeUtil.calcDiff(self, dest);
		}
	}

	@Override
	public Node exec(Frame frame) {
		boolean _if = calcIf(frame, type);
		if (_if) return next;
		frame.applyDiff(diff);
		return target;
	}

	static boolean calcIf(Frame frame, byte type) {
		KType b = frame.pop();

		boolean v = false;
		switch (type) {
			case TRUE - 53:
				v = b.asBool();
				break;
			case JSLexer.lss - 53:
			case JSLexer.gtr - 53:
			case JSLexer.geq - 53:
			case JSLexer.leq - 53: {
				KType a = frame.pop();
				//if (!a.canCastTo(Type.INT) || !b.canCastTo(Type.INT))
				//    throw new IllegalArgumentException("operand is not number: " + a.getClass().getName() + ", " + b.getClass().getName());

				if (!a.isInt() || !b.isInt()) {
					final double aa = a.asDouble(), bb = b.asDouble();
					switch (type) {
						case JSLexer.lss - 53:
							v = aa < bb;
							break;
						case JSLexer.gtr - 53:
							v = aa > bb;
							break;
						case JSLexer.geq - 53:
							v = aa >= bb;
							break;
						case JSLexer.leq - 53:
							v = aa <= bb;
							break;
					}
				} else {
					final int aa = a.asInt(), bb = b.asInt();
					switch (type) {
						case JSLexer.lss - 53:
							v = aa < bb;
							break;
						case JSLexer.gtr - 53:
							v = aa > bb;
							break;
						case JSLexer.geq - 53:
							v = aa >= bb;
							break;
						case JSLexer.leq - 53:
							v = aa <= bb;
							break;
					}
				}
			}
			break;
			case JSLexer.feq - 53: {
				KType a = frame.pop();
				switch (b.getType()) {
					case BOOL:
					case NULL:
					case FUNCTION:
					case UNDEFINED:
					case OBJECT:
					case ERROR:
						v = a == b; // boolean compare
						break;
					case DOUBLE:
					case INT:
						switch (a.getType()) {
							case INT:
							case DOUBLE:
								v = a.equalsTo(b);
								break;
						}
						break;
					case STRING:
						v = b.getType() == Type.STRING && a.asString().equals(b.asString());
						break;
				}
			}
			break;
			case JSLexer.equ - 53:
			case JSLexer.neq - 53: {
				KType a = frame.pop();
				v = (type == (JSLexer.equ - 53)) == a.equalsTo(b);
			}
			break;
		}

		return v;
	}

	@Override
	public String toString() {
		String k;

		switch (type) {
			case TRUE - 53:
				k = "false";
				break;
			case JSLexer.lss - 53:
				k = ">=";
				break;
			case JSLexer.gtr - 53:
				k = "<=";
				break;
			case JSLexer.geq - 53:
				k = "<";
				break;
			case JSLexer.leq - 53:
				k = ">";
				break;
			case JSLexer.feq - 53:
				k = "!===";
				break;
			case JSLexer.equ - 53:
				k = "!=";
				break;
			case JSLexer.neq - 53:
				k = "==";
				break;
			default:
				k = "Undefined" + type;
				break;
		}
		return "If " + k + " => " + target;
	}
}
