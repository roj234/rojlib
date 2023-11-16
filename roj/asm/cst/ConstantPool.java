package roj.asm.cst;

import roj.asm.AsmShared;
import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.List;
import java.util.function.Consumer;

import static roj.asm.cst.Constant.*;

/**
 * @author Roj234
 * @version 1.4
 * @since 2021/5/29 17:16
 */
public class ConstantPool {
	public static final int ONLY_STRING = -1, BYTE_STRING = 0, CHAR_STRING = 1;

	private final SimpleList<Constant> constants;
	private final MyHashSet<Constant> refMap;
	private int length;

	public ConstantPool() {
		constants = new SimpleList<>();
		refMap = new MyHashSet<>();
	}

	public ConstantPool(int size) {
		constants = new SimpleList<>(size);
		refMap = new MyHashSet<>(size);
	}

	public void read(DynByteBuf r, int stringDecodeType) {
		int len = r.readUnsignedShort()-1;

		constants.clear();
		constants.ensureCapacity(len);
		constants.i_setSize(len);

		refMap.clear();
		refMap.ensureCapacity(len);

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
			Constant c = readConstant(r, csts, i, decodeUtf);
			if (c == null) c = (Constant) csts[i];

			csts[i++] = c;
			c.setIndex(i);
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
				case UTF: case CLASS:
					Constant c = readConstant(r, csts, i, false);
					if (c == null) c = (Constant) csts[i];
					if (listener != null) listener.accept(c);

					csts[i++] = c;
					c.setIndex(i);
					break;
				case INT: case FLOAT:
				case NAME_AND_TYPE:
				case FIELD: case METHOD: case INTERFACE:
				case DYNAMIC:
				case INVOKE_DYNAMIC:
					r.rIndex += 5;
					i++;
					break;
				case LONG: case DOUBLE:
					r.rIndex += 9;
					i += 2;
					break;
				case METHOD_TYPE:
				case MODULE:
				case PACKAGE:
				case STRING:
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

				if (arr[i] != null) {
					try {
						((CstUTF) arr[i]).data = data;
					} catch (Exception e) {
						typeError(i, b, arr[i]);
					}
					return null;
				}
				return new CstUTF(data);
			}
			case INT: return new CstInt(r.readInt());
			case FLOAT: return new CstFloat(r.readFloat());
			case LONG: return new CstLong(r.readLong());
			case DOUBLE: return new CstDouble(r.readDouble());

			case METHOD_TYPE:
			case MODULE:
			case PACKAGE:
			case CLASS:
			case STRING: {
				int id = r.readUnsignedShort()-1;
				CstUTF utf;
				if (arr[id] == null) arr[id] = utf = new CstUTF();
				else utf = (CstUTF) arr[id];

				if (arr[i] != null) {
					try {
						((CstRefUTF)arr[i]).setValue(utf);
					} catch (Exception e) {
						typeError(i, b, arr[i]);
					}
					return null;
				}

				switch (b) {
					case METHOD_TYPE: return new CstMethodType(utf);
					case MODULE: return new CstModule(utf);
					case PACKAGE: return new CstPackage(utf);
					case CLASS: return new CstClass(utf);
					default: return new CstString(utf);
				}
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

				if (arr[i] != null) {
					try {
						CstNameAndType nat = (CstNameAndType) arr[i];
						nat.name(name);
						nat.setType(type);
					} catch (Exception e) {
						typeError(i, b, arr[i]);
					}
					return null;
				}

				return new CstNameAndType(name, type);
			}
			case FIELD:
			case METHOD:
			case INTERFACE: {
				int id = r.readUnsignedShort()-1;
				CstClass clz;
				if (arr[id] == null) arr[id] = clz = new CstClass();
				else clz = (CstClass) arr[id];

				CstNameAndType nat;
				id = r.readUnsignedShort()-1;
				if (arr[id] == null) arr[id] = nat = new CstNameAndType();
				else nat = (CstNameAndType) arr[id];

				switch (b) {
					case FIELD: return new CstRefField(clz, nat);
					case METHOD: return new CstRefMethod(clz, nat);
					default: return new CstRefItf(clz, nat);
				}
			}
			case DYNAMIC:
			case INVOKE_DYNAMIC: {
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
	private static void typeError(int i, int b, Object o) { throw new IllegalArgumentException("pool["+i+"]的常量类型不匹配: 期待: "+Constant.toString(b)+" 得到: "+o); }

	public final List<Constant> array() { return constants; }
	public final Constant array(int i) { return i-- == 0 ? Helpers.maybeNull() : constants.get(i); }
	public final Constant get(DynByteBuf r) { return array(r.readUnsignedShort()); }
	public String getRefName(DynByteBuf r) {
		int id = r.readUnsignedShort()-1;
		return id < 0 ? null : ((CstRefUTF) constants.get(id)).name().str();
	}

	private final CstTop fp = AsmShared.local().fp;

	private void initRefMap() {
		if (!refMap.isEmpty()) return;
		Object[] cst = constants.getInternalArray();
		for (int i = 0; i < constants.size(); i++) {
			Constant c = (Constant) cst[i];
			if (c == CstTop.TOP) continue;

			Constant c1 = refMap.intern(c);
			if (c != c1) c.setIndex(c1.getIndex());
		}
	}

	public void setUTFValue(CstUTF c, String str) {
		int id = c.getIndex()-1;
		if (id < 0 || id >= constants.size() || constants.getInternalArray()[id] != c) {
			throw new IllegalArgumentException(c + "不在该常量池中");
		}

		boolean rm;
		if (!refMap.isEmpty()) {
			rm = refMap.remove(c);
			assert rm : "不在该常量池中";
			refMap.add(c);
		} else {
			rm = false;
		}

		int prev = c._length();
		int curr = ByteList.byteCountDioUTF(str);

		length += curr - prev;

		c.data = str;

		if (rm) refMap.add(c);
	}

	private void addConstant(Constant c) {
		c.setIndex(constants.size()+1);
		refMap.add(c);
		constants.add(c);

		switch (c.type()) {
			case UTF: length += 3 + DynByteBuf.byteCountDioUTF(((CstUTF) c).str()); break;
			case INT: case FLOAT:
			case NAME_AND_TYPE: case INVOKE_DYNAMIC:
			case METHOD: case FIELD: case INTERFACE:
				length += 5; break;
			case LONG: case DOUBLE:
				length += 9;
				constants.add(CstTop.TOP);
				break;
			case METHOD_TYPE:
			case STRING: case CLASS:
			case MODULE: case PACKAGE:
				length += 3;
				break;
			case METHOD_HANDLE: length += 4; break;
			case _TOP_: break;
			default: throw new IllegalStateException("Unknown type " + c.type());
		}

		if (listener != null) listener.accept(c);
	}

	public CstUTF getUtf(CharSequence msg) {
		initRefMap();

		CstUTF utf;
		Constant o = refMap.find(fp.set(msg));
		if (o == fp) {
			addConstant(utf = new CstUTF(msg.toString()));
		} else if (!msg.equals((utf = (CstUTF) o).str())) {
			throw new IllegalStateException("Unfit utf id!!! G: '" + utf.str() + "' E: '" + msg + '\'');
		}

		return utf;
	}
	public int getUtfId(CharSequence msg) { return getUtf(msg).getIndex(); }

	public CstNameAndType getDesc(String name, String type) {
		CstUTF uName = getUtf(name);
		CstUTF uType = getUtf(type);

		Object ref = refMap.find(fp.set(uName, uType));
		if (ref == fp) {
			CstNameAndType t = new CstNameAndType();
			t.name(uName);
			t.setType(uType);
			addConstant(t);
			ref = t;
		}

		return (CstNameAndType) ref;
	}
	public int getDescId(String name, String desc) { return getDesc(name, desc).getIndex(); }

	public CstClass getClazz(String name) {
		CstUTF uName = getUtf(name);

		Object ref = refMap.find(fp.set(CLASS, uName));
		if (ref == fp) {
			CstClass t = new CstClass();
			t.setValue(uName);
			addConstant(t);
			ref = t;
		}

		return (CstClass) ref;
	}
	public int getClassId(String owner) { return getClazz(owner).getIndex(); }

	public CstRefMethod getMethodRef(String owner, String name, String desc) {
		CstClass clazz = getClazz(owner);
		CstNameAndType nat = getDesc(name, desc);

		Object ref = refMap.find(fp.set(METHOD, clazz, nat));
		if (ref == fp) {
			CstRefMethod t = new CstRefMethod();
			t.clazz(clazz);
			t.desc(nat);
			addConstant(t);
			ref = t;
		}

		return (CstRefMethod) ref;
	}
	public int getMethodRefId(String owner, String name, String desc) { return getMethodRef(owner, name, desc).getIndex(); }

	public CstRefField getFieldRef(String owner, String name, String desc) {
		CstClass clazz = getClazz(owner);
		CstNameAndType nat = getDesc(name, desc);

		Object ref = refMap.find(fp.set(FIELD, clazz, nat));
		if (ref == fp) {
			CstRefField t = new CstRefField();
			t.clazz(clazz);
			t.desc(nat);
			addConstant(t);
			ref = t;
		}

		return (CstRefField) ref;
	}
	public int getFieldRefId(String owner, String name, String desc) { return getFieldRef(owner, name, desc).getIndex(); }

	public CstRefItf getItfRef(String owner, String name, String desc) {
		CstClass clazz = getClazz(owner);
		CstNameAndType nat = getDesc(name, desc);

		Object ref = refMap.find(fp.set(INTERFACE, clazz, nat));
		if (ref == fp) {
			CstRefItf t = new CstRefItf();
			t.clazz(clazz);
			t.desc(nat);
			addConstant(t);
			ref = t;
		}

		return (CstRefItf) ref;
	}
	public int getItfRefId(String owner, String name, String desc) { return getItfRef(owner, name, desc).getIndex(); }

	private CstRef getRefByType(String owner, String name, String desc, byte type) {
		switch (type) {
			case FIELD: return getFieldRef(owner, name, desc);
			case METHOD: return getMethodRef(owner, name, desc);
			default: // no default branch
			case INTERFACE: return getItfRef(owner, name, desc);
		}
	}

	public CstMethodHandle getMethodHandle(String owner, String name, String desc, byte kind, byte type) {
		CstRef ref = getRefByType(owner, name, desc, type);

		//fp.set(kind, ref);
		CstMethodHandle handle = new CstMethodHandle(kind, -1);
		handle.setRef(ref);

		CstMethodHandle found = (CstMethodHandle) refMap.find(handle);

		if (found == handle) {
			addConstant(handle);
		}
		return found;
	}
	public int getMethodHandleId(String owner, String name, String desc, byte kind, byte type) { return getMethodHandle(owner, name, desc, kind, type).getIndex(); }

	public CstDynamic getInvokeDyn(boolean isMethod, int table, String name, String desc) {
		CstNameAndType nat = getDesc(name, desc);

		CstDynamic handle = new CstDynamic(isMethod, table, nat);

		CstDynamic found = (CstDynamic) refMap.find(handle);

		if (found == handle) {
			addConstant(handle);
		}
		return found;
	}
	public int getInvokeDynId(int table, String name, String desc) { return getInvokeDyn(true, table, name, desc).getIndex(); }
	public int getDynId(int table, String name, String desc) { return getInvokeDyn(false, table, name, desc).getIndex(); }

	public CstPackage getPackage(String owner) {
		CstUTF name = getUtf(owner);

		Object ref = refMap.find(fp.set(PACKAGE, name));
		if (ref == fp) {
			CstPackage t = new CstPackage();
			t.setValue(name);
			addConstant(t);
			ref = t;
		}

		return (CstPackage) ref;
	}
	public int getPackageId(String owner) { return getPackage(owner).getIndex(); }

	public CstModule getModule(String owner) {
		CstUTF name = getUtf(owner);

		Object ref = refMap.find(fp.set(MODULE, name));
		if (ref == fp) {
			CstModule t = new CstModule();
			t.setValue(name);
			addConstant(t);
			ref = t;
		}

		return (CstModule) ref;
	}
	public int getModuleId(String owner) { return getModule(owner).getIndex(); }

	public int getIntId(int i) {
		initRefMap();

		Constant ref = refMap.find(fp.set(i));
		if (ref == fp) addConstant(ref = new CstInt(i));
		return ref.getIndex();
	}
	public int getLongId(long i) {
		initRefMap();

		Constant ref = refMap.find(fp.set(i));
		if (ref == fp) addConstant(ref = new CstLong(i));
		return ref.getIndex();
	}
	public int getFloatId(float i) {
		initRefMap();

		Constant ref = refMap.find(fp.set(i));
		if (ref == fp) addConstant(ref = new CstFloat(i));
		return ref.getIndex();
	}
	public int getDoubleId(double i) {
		initRefMap();

		Constant ref = refMap.find(fp.set(i));
		if (ref == fp) addConstant(ref = new CstDouble(i));
		return ref.getIndex();
	}

	@SuppressWarnings("unchecked")
	public <T extends Constant> T reset(T c) {
		//assert c != null;
		switch (c.type()) {
			case DYNAMIC:
			case INVOKE_DYNAMIC: {
				CstDynamic dyn = (CstDynamic) c;
				dyn.setDesc(reset(dyn.desc()));
			}
			break;
			case CLASS:
			case STRING:
			case METHOD_TYPE:
			case MODULE:
			case PACKAGE: {
				CstRefUTF ref = (CstRefUTF) c;
				ref.setValue(reset(ref.name()));
			}
			break;
			case METHOD_HANDLE: {
				CstMethodHandle ref = ((CstMethodHandle) c);
				ref.setRef(reset(ref.getRef()));
			}
			break;
			case METHOD:
			case INTERFACE:
			case FIELD: {
				CstRef ref = (CstRef) c;
				ref.clazz(reset(ref.clazz()));
				ref.desc(reset(ref.desc()));
			}
			break;
			case NAME_AND_TYPE: {
				CstNameAndType nat = (CstNameAndType) c;
				nat.name(reset(nat.name()));
				nat.setType(reset(nat.getType()));
			}
			break;
			case UTF:
				((CstUTF) c).str();
			case INT:
			case DOUBLE:
			case FLOAT:
			case LONG:
				// No need to do anything, just append it
				break;
			default: throw new IllegalArgumentException("Unsupported type: " + c.type());
		}

		int id = c.getIndex()-1;
		if (id >= 0 && id < constants.size() && constants.getInternalArray()[id] == c) {
			return c;
		}

		initRefMap();
		T t = (T) refMap.find(c);
		if (t != c) return t;

		// TODO thread safe implement
		addConstant(c);
		return c;
	}

	public int byteLength() {
		if (length == 0) throw new IllegalStateException("This pool is not ready to write");
		return length;
	}

	public void write(DynByteBuf w) {
		w.putShort(constants.size()+1);
		List<Constant> csts = constants;
		for (int i = 0; i < csts.size(); i++)
			csts.get(i).write(w);
	}

	@Override
	public String toString() {
		Object[] array = new Object[constants.size()*4];
		int i = 0;
		for (int j = 0; j < constants.size(); j++) {
			Constant c = constants.get(j);
			String s1 = Integer.toString(c.getIndex());
			int k = s1.length()+2;
			array[i++] = s1;
			s1 = Constant.toString(c.type());
			if (s1 == null) s1 = "TOP";
			k += s1.length();
			array[i++] = s1;
			array[i++] = s1.equals("TOP")?"/":c.toString().substring(k);
			array[i++] = IntMap.UNDEFINED;
		}
		return TextUtil.prettyTable(new StringBuilder("constants[" + constants.size() + "]=["), "    ", array, " ", " ").append("]").toString();
	}

	public void clear() {
		constants.clear();
		refMap.clear();
		length = 0;
		if (listener != null) listener.accept(null);
	}

	Consumer<Constant> listener;
	public void setAddListener(Consumer<Constant> x) { listener = x; }
}