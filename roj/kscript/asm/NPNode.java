package roj.kscript.asm;

import roj.kscript.api.IArray;
import roj.kscript.type.*;

/**
 * @author Roj234
 * @since 2020/9/27 13:23
 */
public final class NPNode extends Node {
	byte code;

	public NPNode(Opcode code) {
		this.code = (byte) code.ordinal();
	}

	@Override
	public Opcode getCode() {
		return Opcode.byId(code);
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Node exec(Frame f) {
		KType a, b;
		switch (code) {
			case 0://GET_OBJ:
				b = f.pop();
				a = f.last();

				if (a.canCastTo(Type.ARRAY) && b.isInt()) {
					f.setLast(a.asArray().get(b.asInt()).memory(4));
				} else {
					f.setLast(a.asObject().get(b.asString()).memory(4));
				}
				break;
			case 1://PUT_OBJ:
			{
				KType val = f.pop().memory(3);
				b = f.pop();
				a = f.pop();

				if (a.canCastTo(Type.ARRAY) && b.isInt()) {
					a.asArray().set(b.asInt(), val);
				} else {
					a.asObject().put(b.asString(), val);
				}
			}
			break;
			case 2://DELETE_OBJ:
				b = f.pop();
				a = f.pop();

				//if (a.canCastTo(Type.ARRAY) && b.isInt()) {
				//    f.push(KBool.valueOf(a.asArray().delete(b.asInt())));
				//} else {
				f.push(KBool.valueOf(a.asObject().delete(b.asString())));
				//}
				break;
			case 3://ADD_ARRAY:
				b = f.pop();
				a = f.pop().memory(3);

				a.asArray().add(b);
				break;
			case 5://INSTANCE_OF:
				b = f.pop();
				a = f.last();
				f.setLast(KBool.valueOf(a.canCastTo(Type.OBJECT) && a.asObject().isInstanceOf(b.asObject())));
				break;
			case 6://POP:
				f.pop();
				break;
			case 7://DUP:
				f.push(f.last().memory(2));
				break;
			case 8://DUP2:
				// a,b => a,b , a,b
				a = f.last(1);
				b = f.last();
				f.push(a.memory(2));
				f.push(b.memory(2));
				break;
			case 9://SWAP:
			{
				int i = f.stackUsed - 1;
				if (i < 1) throw new ArrayIndexOutOfBoundsException(i);
				KType[] arr = f.stack;
				KType swp = arr[i];
				arr[i] = arr[i - 1];
				arr[i - 1] = swp;
			}
			break;
			case 10://SWAP3:
			{
				int i = f.stackUsed - 1;
				if (i < 3) throw new ArrayIndexOutOfBoundsException(i);

				// obj idx val val
				//   => val obj idx val

				KType[] arr = f.stack;
				KType swp = arr[i];
				for (int j = 0; j < 3; j++) { // slow move
					arr[i - j] = arr[i - j - 1];
				}
				arr[i - 3] = swp;
			}
			break;
			case 14://RETURN:
				f.result = f.pop();
			case 15://RETURN_EMPTY:
				return null;
			case 17://THROW:
				// no pop since stack will be cleared
				throw f.last().asKError().getOrigin();
			case 18://NOT:
				f.setLast(KBool.valueOf(!f.last().asBool()));
				break;
			case 19://OR:
			case 20://XOR:
			case 21://AND:
				//b = f.pop();
				//a = f.last();

                /*if (a.getType() == Type.BOOL) {
                    boolean v = false;
                    boolean aa = a.asBool(), bb = b.asBool();
                    switch (code) {
                        case 19:
                            v = aa | bb;
                            break;
                        case 20:
                            v = aa ^ bb;
                            break;
                        case 21:
                            v = aa & bb;
                            break;
                    }
                    f.setLast(KBool.valueOf(v));
                } else */
			{
				int bb = f.pop().asInt();
				a = f.last();
				int aa = a.asInt();

				int v = 0;
				switch (code) {
					case 19:
						v = aa | bb;
						break;
					case 20:
						v = aa ^ bb;
						break;
					case 21:
						v = aa & bb;
						break;
				}
				a.setIntValue(v);
			}
			break;
			case 22://SHIFT_L:
			case 23://SHIFT_R:
			case 24://U_SHIFT_R:
				b = f.pop();
				a = f.last();

				a.asInt();
				if (!a.isInt()) {
					a.setIntValue(0);
				} else {
					int val = b.asInt();

					int it = a.asInt();
					switch (code) {
						case 22:
							it <<= val;
							break;
						case 23:
							it >>= val;
							break;
						case 24:
							it >>>= val;
							break;
					}

					a.setIntValue(it);
				}
				break;
			case 25://REVERSE:
				a = f.last();
				a.asInt();
				a.setIntValue(a.isInt() ? ~a.asInt() : 0);
				break;
			case 26://NEGATIVE:
				a = f.last();

				if (a.isInt()) {
					a.setIntValue(-a.asInt());
				} else {
					a.setDoubleValue(-a.asDouble());
				}
				break;
			case 28://ADD:
				b = f.pop();
				a = f.last();

				if (a.isString() || b.isString()) { // string append
					StringBuilder sb;
					if (a.getType() != Type.JAVA_OBJECT || !(a.asJavaObject(Object.class).getObject() instanceof StringBuilder)) {
						f.setLast(new KJavaObject<>(sb = new StringBuilder(a.asString())));
					} else {
						sb = a.asJavaObject(StringBuilder.class).getObject();
					}
					sb.append(b.asString());
					break;
				}

				// Number
				if (!b.isInt() || !a.isInt()) {
					a.setDoubleValue(a.asDouble() + b.asDouble());
				} else {
					a.setIntValue(a.asInt() + b.asInt());
				}
				break;
			case 29://SUB:
			case 30://MUL:
			case 31://DIV:
				b = f.pop();
				a = f.last();

				// Number
				if (!b.isInt() || !a.isInt()) {
					double aa = a.asDouble();
					double bb = b.asDouble();

					switch (code) {
						case 29:
							aa = aa - bb;
							break;
						case 30:
							aa = aa * bb;
							break;
						case 31:
							aa = aa / bb;
							break;
					}

					a.setDoubleValue(aa);
				} else {
					int aa = a.asInt();
					int bb = b.asInt();

					switch (code) {
						case 28:
							aa = aa + bb;
							break;
						case 29:
							aa = aa - bb;
							break;
						case 30:
							aa = aa * bb;
							break;
					}

					a.setIntValue(aa);
				}
				break;
			case 32://MOD:
				b = f.pop();
				a = f.last();
				a.setIntValue(b.isInt() ? a.asInt() % b.asInt() : 0);
				break;
			case 33://POW:
				b = f.pop();
				a = f.last();
				a.setDoubleValue(Math.pow(a.asDouble(), b.asDouble()));
				break;
			case 35://THIS:
				f.push(f.$this);
				break;
			case 36://ARGUMENTS:
				f.push(f.args);
				break;
			case 37://CAST_INT:
				a = f.last();
				if (!a.isInt()) f.setLast(KInt.valueOf(a.asInt()));
				break;
			case 38://CAST_BOOL:
				a = f.last();
				if (!(a instanceof KBool)) f.setLast(KBool.valueOf(a.asBool()));
				break;
			case 39://SPREAD:
			{
				IArray source = f.pop().asArray(); // do not optimize, execution order!
				f.last().asArray().addAll(source);
			}
			break;
			default:
				throw new IllegalArgumentException("Unsupported op " + code);
		}

		return next;
	}

}
