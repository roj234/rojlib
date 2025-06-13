package roj.asm.cp;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.AsmCache;
import roj.asm.attr.BootstrapMethods;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.function.Consumer;

import static roj.asm.cp.Constant.*;

/**
 * @author Roj234
 * @version 2.0
 * @since 2021/5/29 17:16
 */
public final class ConstantPool {
	public static final int ONLY_STRING = -1, BYTE_STRING = 0, CHAR_STRING = 1;

	private final ArrayList<Constant> constants;
	private HashSet<Constant> refMap;
	private int length;
	private boolean isForWrite;

	public ConstantPool(int size) {constants = new ArrayList<>(size);}
	public ConstantPool() {
		constants = new ArrayList<>();
		refMap = new HashSet<>();
	}

	public void read(DynByteBuf r, @MagicConstant(intValues = {ONLY_STRING,BYTE_STRING,CHAR_STRING}) int stringDecodeType) {
		int len = r.readUnsignedShort()-1;
		if (len < 0) throw new IllegalArgumentException("size error: "+len);

		constants.clear();
		constants.ensureCapacity(len);
		constants._setSize(len);

		if (refMap != null) refMap.clear();

		Object[] csts = constants.getInternalArray();

		int begin = r.rIndex;

		if (stringDecodeType == ONLY_STRING) {
			readName(r, csts, len, listener);

			length = r.rIndex - begin;
			return;
		}

		boolean decodeUtf = stringDecodeType != BYTE_STRING;

		int twoPass = -1;
		int i = 0;
		while (i < len) {
			Constant c;
			try {
				c = readConstant(r, csts, i, decodeUtf);
			} catch (Exception e) {
				String cst;
				try {
					cst = String.valueOf(csts[i]);
				} catch (Exception ignored) {
					cst = csts[i].getClass().getName();
				}

				throw new IllegalArgumentException("常量池["+i+"]解析失败！当前值: "+cst, e);
			}

			csts[i++] = c;
			c.index = (char) i;
			if (listener != null) listener.accept(c);
			switch (c.type()) {
				case LONG: case DOUBLE:
					csts[i++] = CstTop.TOP;
					break;
				case METHOD_HANDLE:
					if (twoPass < 0) twoPass = i-1;
					break;
			}
		}

		if (twoPass >= 0) {
			i = twoPass;
			while (i < len) {
				Constant c = (Constant) csts[i++];
				if (c.type() == METHOD_HANDLE) {
					CstMethodHandle mh = (CstMethodHandle) c;
					mh.setRef((CstRef) csts[mh.getRefIndex()-1]);
				}
			}
		}

		length = r.rIndex - begin;
	}

	private static void readName(DynByteBuf r, Object[] csts, int len, Consumer<Constant> listener) {
		int i = 0;
		while (i < len) {
			switch (r.get(r.rIndex)) {
				case UTF, CLASS:
					var c = readConstant(r, csts, i, false);
					if (c == null) c = (Constant) csts[i];
					if (listener != null) listener.accept(c);

					csts[i++] = c;
					c.index = (char) i;
					break;
				case INT, FLOAT, NAME_AND_TYPE, FIELD, METHOD, INTERFACE, DYNAMIC, INVOKE_DYNAMIC:
					r.rIndex += 5;
					i++;
					break;
				case LONG: case DOUBLE:
					r.rIndex += 9;
					i += 2;
					break;
				case METHOD_TYPE, MODULE, PACKAGE, STRING:
					r.rIndex += 3;
					i++;
					break;
				case METHOD_HANDLE:
					r.rIndex += 4;
					i++;
					break;
				default: throw new IllegalStateException("Unknown constant since "+r.dump());
			}
		}
	}

	private static Constant readConstant(DynByteBuf r, Object[] arr, int i, boolean parseUTF) {
		int b = r.readUnsignedByte();
		switch (b) {
			case UTF: {
				Object data;
				if (parseUTF) data = r.readUTF();
				else {
					int len = r.readUnsignedShort();
					data = len <= 16 ? r.readBytes(len) : r.slice(len);
				}

				CstUTF known = (CstUTF) arr[i];
				if (known != null) {
					known.data = data;
					return known;
				}
				return new CstUTF(data);
			}
			case INT: return new CstInt(r.readInt());
			case FLOAT: return new CstFloat(r.readFloat());
			case LONG: return new CstLong(r.readLong());
			case DOUBLE: return new CstDouble(r.readDouble());

			case METHOD_TYPE, MODULE, PACKAGE, CLASS, STRING: {
				int id = r.readUnsignedShort()-1;
				CstUTF utf;
				if (arr[id] == null) arr[id] = utf = new CstUTF();
				else utf = (CstUTF) arr[id];

				CstRefUTF known = (CstRefUTF) arr[i];
				if (known != null) {
					known.setValue(utf);
					return known;
				}

				return switch (b) {
					case METHOD_TYPE -> new CstMethodType(utf);
					case MODULE -> new CstRefUTF.Module(utf);
					case PACKAGE -> new CstRefUTF.Package(utf);
					case CLASS -> new CstClass(utf);
					default -> new CstString(utf);
				};
			}
			case NAME_AND_TYPE: {
				int id = r.readUnsignedShort()-1;
				CstUTF name;
				if (arr[id] == null) arr[id] = name = new CstUTF();
				else name = (CstUTF) arr[id];

				CstUTF type;
				id = r.readUnsignedShort()-1;
				if (arr[id] == null) arr[id] = type = new CstUTF();
				else type = (CstUTF) arr[id];

				CstNameAndType known = (CstNameAndType) arr[i];
				if (known != null) {
					known.name(name);
					known.rawDesc(type);
					return known;
				}

				return new CstNameAndType(name, type);
			}
			case FIELD, METHOD, INTERFACE: {
				int id = r.readUnsignedShort()-1;
				CstClass clz;
				if (arr[id] == null) arr[id] = clz = new CstClass();
				else clz = (CstClass) arr[id];

				CstNameAndType nat;
				id = r.readUnsignedShort()-1;
				if (arr[id] == null) arr[id] = nat = new CstNameAndType();
				else nat = (CstNameAndType) arr[id];

				return switch (b) {
					case FIELD -> new CstRef.Field(clz, nat);
					case METHOD -> new CstRef.Method(clz, nat);
					default -> new CstRef.Interface(clz, nat);
				};
			}
			case DYNAMIC, INVOKE_DYNAMIC: {
				int id = r.readUnsignedShort(r.rIndex + 2)-1;
				CstNameAndType desc;
				if (arr[id] == null) arr[id] = desc = new CstNameAndType();
				else desc = (CstNameAndType) arr[id];

				CstDynamic dyn = new CstDynamic(b == INVOKE_DYNAMIC, r.readUnsignedShort(), desc);
				r.rIndex += 2;
				return dyn;
			}
			case METHOD_HANDLE: return new CstMethodHandle(r.readByte(), r.readUnsignedShort());
			default: throw new IllegalArgumentException("无效的常量类型 " + b);
		}
	}

	public final List<Constant> data() {return constants;}
	public final @Nullable Constant getNullable(DynByteBuf r) {
		int i = r.readUnsignedShort()-1;
		return i < 0 ? null : constants.get(i);
	}
	@SuppressWarnings("unchecked")
	public final @NotNull <T extends Constant> T get(DynByteBuf r) {return (T) constants.getInternalArray()[r.readUnsignedShort()-1];}
	public final @Nullable String getRefName(DynByteBuf r) {
		int id = r.readUnsignedShort()-1;
		return id < 0 ? null : ((CstClass) constants.getInternalArray()[id]).value().str();
	}
	public final @NotNull String getRefName(DynByteBuf r, int type) {
		var c = (CstRefUTF) constants.getInternalArray()[r.readUnsignedShort()-1];
		if (c.type() != type) throw new IllegalStateException("excepting"+Constant.toString(type)+" but got "+c);
		return c.value().str();
	}
	public final @NotNull CstRef getRef(DynByteBuf r, boolean isField) {
		var c = (CstRef) constants.getInternalArray()[r.readUnsignedShort()-1];
		if (c.type() == FIELD != isField) throw new IllegalStateException("excepting" + (isField ? "field" : "method") + "but got "+c);
		return c;
	}

	private void initRefMap() {
		if (refMap == null) refMap = new HashSet<>(constants.size());
		else {
			if (!refMap.isEmpty()) return;
			refMap.ensureCapacity(constants.size());
		}

		Object[] cst = constants.getInternalArray();
		for (int i = 0; i < constants.size(); i++) {
			Constant c = (Constant) cst[i];
			if (c == CstTop.TOP) continue;

			Constant c1 = refMap.intern(c);
			if (c != c1) c.index = c1.index;
		}

		if (!isForWrite) {
			isForWrite = true;
			AsmCache.getInstance().getCpWriter(constants);
		}
	}
	private void addConstant(Constant c) {
		refMap.add(c);
		constants.add(c);
		int size = constants.size();
		c.index = (char) size;
		if (size >= 0xFFFF) throw new UnsupportedOperationException("constant overflow!");

		switch (c.type()) {
			case UTF -> length += 3 + DynByteBuf.byteCountDioUTF(((CstUTF) c).str());
			case INT, FLOAT, NAME_AND_TYPE, INVOKE_DYNAMIC, DYNAMIC, METHOD, FIELD, INTERFACE -> length += 5;
			case LONG, DOUBLE -> {
				length += 9;
				constants.add(CstTop.TOP);
			}
			case METHOD_TYPE, STRING, CLASS, MODULE, PACKAGE -> length += 3;
			case METHOD_HANDLE -> length += 4;
			default -> throw new IllegalStateException("Unknown type " + c.type());
		}

		if (listener != null) listener.accept(c);
	}

	public void setUTFValue(CstUTF utf, String str) {
		int id = utf.index-1;
		if (id < 0 || id >= constants.size() || constants.getInternalArray()[id] != utf) {
			throw new IllegalArgumentException(utf+"不在该常量池中");
		}

		boolean rm;
		if (!refMap.isEmpty()) {
			rm = refMap.remove(utf);
			assert rm : "不在该常量池中";
		} else {
			rm = false;
		}

		int prev = utf._length();
		int curr = ByteList.byteCountDioUTF(str);

		length += curr - prev;

		utf.data = str;

		if (rm) refMap.add(utf);
	}

	public CstUTF getUtf(CharSequence str) {
		initRefMap();

		CstUTF utf;
		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(str));
		if (found == find) {
			addConstant(utf = new CstUTF(str.toString()));
		} else if (!str.equals((utf = (CstUTF) found).str())) {
			throw new IllegalStateException("UTF类型的值被外部修改，期待 '"+str+"' 实际 '"+utf.str()+'\'');
		}

		return utf;
	}
	public int getUtfId(CharSequence msg) {return getUtf(msg).index;}

	public CstNameAndType getDesc(String name, String type) {
		var uName = getUtf(name);
		var uType = getUtf(type);

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(uName, uType));
		if (found == find) addConstant(found = new CstNameAndType(uName, uType));
		return (CstNameAndType) found;
	}
	public int getDescId(String name, String desc) {return getDesc(name, desc).index;}

	public CstClass getClazz(String name) {
		var utf = getUtf(name);

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(CLASS, utf));
		if (found == find) addConstant(found = new CstClass(utf));
		return (CstClass) found;
	}
	public int getClassId(String owner) {return getClazz(owner).index;}

	public CstRef getRefByType(String owner, String name, String desc, @MagicConstant(intValues = {METHOD, FIELD, INTERFACE}) byte type) {
		var clazz = getClazz(owner);
		var nat = getDesc(name, desc);

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(type, clazz, nat));
		if (found == find) {
			found = switch (type) {
				case FIELD -> new CstRef.Field(clazz, nat);
				case METHOD -> new CstRef.Method(clazz, nat);
				//case INTERFACE
				default -> new CstRef.Interface(clazz, nat);
			};
			addConstant(found);
		}

		return (CstRef) found;
	}
	public int getMethodRefId(String owner, String name, String desc) {return getRefByType(owner, name, desc, METHOD).index;}
	public int getFieldRefId(String owner, String name, String desc) {return getRefByType(owner, name, desc, FIELD).index;}
	public int getItfRefId(String owner, String name, String desc) {return getRefByType(owner, name, desc, INTERFACE).index;}

	public CstMethodHandle getMethodHandle(String owner, String name, String desc, @MagicConstant(valuesFromClass = BootstrapMethods.Kind.class) byte kind, @MagicConstant(intValues = {METHOD, FIELD, INTERFACE}) byte type) {
		var ref = getRefByType(owner, name, desc, type);

		var find = new CstMethodHandle(kind, ref);
		var found = (CstMethodHandle) refMap.find(find);
		if (found == find) addConstant(find);
		return found;
	}
	public int getMethodHandleId(String owner, String name, String desc, @MagicConstant(valuesFromClass = BootstrapMethods.Kind.class) byte kind, @MagicConstant(intValues = {METHOD, FIELD, INTERFACE}) byte type) {return getMethodHandle(owner, name, desc, kind, type).index;}

	public CstDynamic getInvokeDyn(boolean isMethod, int table, String name, String desc) {
		var nat = getDesc(name, desc);

		var find = new CstDynamic(isMethod, table, nat);
		var found = (CstDynamic) refMap.find(find);
		if (found == find) addConstant(find);
		return found;
	}
	public int getInvokeDynId(int table, String name, String desc) {return getInvokeDyn(true, table, name, desc).index;}
	public int getLoadDynId(int table, String name, String desc) {return getInvokeDyn(false, table, name, desc).index;}

	public int getPackageId(String owner) {
		var utf = getUtf(owner);

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(PACKAGE, utf));
		if (found == find) addConstant(found = new CstRefUTF.Package(utf));
		return found.index;
	}
	public int getModuleId(String owner) {
		var utf = getUtf(owner);

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(MODULE, utf));
		if (found == find) addConstant(found = new CstRefUTF.Module(utf));
		return found.index;
	}

	public int getIntId(int i) {
		initRefMap();

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(i));
		if (found == find) addConstant(found = new CstInt(i));
		return found.index;
	}
	public int getLongId(long i) {
		initRefMap();

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(i));
		if (found == find) addConstant(found = new CstLong(i));
		return found.index;
	}
	public int getFloatId(float i) {
		initRefMap();

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(i));
		if (found == find) addConstant(found = new CstFloat(i));
		return found.index;
	}
	public int getDoubleId(double i) {
		initRefMap();

		var find = AsmCache.getInstance().fp;
		var found = refMap.find(find.set(i));
		if (found == find) addConstant(found = new CstDouble(i));
		return found.index;
	}

	public int indexOf(Constant s) {return s.index;}
	public int fit(Constant c) {return reset(c).index;}
	@SuppressWarnings({"unchecked", "fallthrough"})
	public <T extends Constant> T reset(T c) {
		switch (c.type()) {
			case DYNAMIC, INVOKE_DYNAMIC: {
				CstDynamic dyn = (CstDynamic) c;
				dyn.setDesc(reset(dyn.desc()));
			}
			break;
			case CLASS, STRING, METHOD_TYPE, MODULE, PACKAGE: {
				CstRefUTF ref = (CstRefUTF) c;
				ref.setValue(reset(ref.value()));
			}
			break;
			case METHOD_HANDLE: {
				CstMethodHandle ref = ((CstMethodHandle) c);
				ref.setRef(reset(ref.getRef()));
			}
			break;
			case METHOD, INTERFACE, FIELD: {
				CstRef ref = (CstRef) c;
				ref.clazz(reset(ref.clazz()));
				ref.nameAndType(reset(ref.nameAndType()));
			}
			break;
			case NAME_AND_TYPE: {
				CstNameAndType nat = (CstNameAndType) c;
				nat.name(reset(nat.name()));
				nat.rawDesc(reset(nat.rawDesc()));
			}
			break;
			case UTF:
				verifyUtf(((CstUTF) c).str());
			case INT, DOUBLE, FLOAT, LONG:
				// No need to do anything, just append it
				break;
			default: throw new IllegalArgumentException("Unsupported type: " + c.type());
		}

		int id = c.index-1;
		if (id >= 0 && id < constants.size() && constants.getInternalArray()[id] == c) {
			return c;
		}

		initRefMap();
		T t = (T) refMap.find(c);
		if (t != c) return t;

		addConstant(c);
		return c;
	}
	static void verifyUtf(String str) {
		if (str.length() >= 0x10000/3) {
			if (str.length() >= 0x10000 || ByteList.byteCountDioUTF(str) >= 0x1000) throw new IllegalArgumentException("UTF8字符串太长，限制是65535字节，"+str.length()+"！");
		}
	}

	public int byteLength() {
		if (length == 0) throw new IllegalStateException("This pool is not ready to write");
		return length;
	}

	public void write(DynByteBuf w, boolean discard) {
		w.putShort(constants.size()+1);
		List<Constant> csts = constants;
		for (int i = 0; i < csts.size(); i++)
			csts.get(i).write(w);

		if (isForWrite) {
			isForWrite = false;
			AsmCache.getInstance().freeCpWriter(constants, discard);
		}
	}

	@Override
	public String toString() {
		Object[] array = new Object[constants.size()*4];
		int i = 0;
		for (int j = 0; j < constants.size(); j++) {
			Constant c = constants.get(j);
			String s1 = Integer.toString(c.index);
			int k = s1.length()+2;
			array[i++] = s1;
			s1 = Constant.toString(c.type());
			if (s1 == null) s1 = "TOP";
			k += s1.length();
			array[i++] = s1;
			array[i++] = s1.equals("TOP")?"/":c.toString().substring(c.type() < 3 || c.type() > 8 ? k : 0);
			array[i++] = IntMap.UNDEFINED;
		}
		return TextUtil.prettyTable(new StringBuilder("constants["+constants.size()+"]=["), "    ", array, " ", " ").append("]").toString();
	}

	public void clear() {
		constants.clear();
		refMap.clear();
		length = 0;
		if (listener != null) listener.accept(null);
	}

	Consumer<Constant> listener;
	public void setAddListener(Consumer<Constant> x) { listener = x; }

	public void checkCollision(DynByteBuf w) {
		for (int i = 0; i < constants.size(); i++) {
			Constant c = constants.get(i);
			if (c instanceof CstUTF u) {
				if (u.data instanceof DynByteBuf w2) {
					if (w2.array() == w.array()) {
						if (w.array() == null) {
							if (w2.address() > w.address() && w2.address() < w.address()+w.capacity())
								throw new AssertionError("请勿将未解析字符串常量的ConstantData写入和来源相同的DynByteBuf之中");
						} else {
							throw new AssertionError("请勿将未解析字符串常量的ConstantData写入和来源相同的DynByteBuf之中");
						}
					}
				}
			}
		}
	}
}