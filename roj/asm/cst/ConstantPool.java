package roj.asm.cst;

import roj.collect.IntMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @version 1.4
 * @since 2021/5/29 17:16
 */
public class ConstantPool {
	private Object[] cst;
	private SimpleList<Constant> constants;
	private MyHashSet<Constant> refMap;
	int length;

	public ConstantPool() {
		this.constants = new SimpleList<>(64);
		this.refMap = new MyHashSet<>(64);
	}

	public ConstantPool(int len) {
		len--;

		this.cst = new Constant[len];
		this.constants = new SimpleList<>();
		this.constants.setRawArray(cst);
		this.constants.i_setSize(len);
		this.refMap = new MyHashSet<>(cst.length);
	}

	public void init(int len) {
		len--;

		Object[] prevArr = constants.getRawArray();
		if (prevArr != null && prevArr.length >= len) {
			cst = prevArr;
		} else {
			cst = new Constant[len];
			constants.setRawArray(cst);
		}

		refMap.clear();
		refMap.ensureCapacity(len);

		constants.i_setSize(len);
	}

	public void read(DynByteBuf r, boolean utfNow) {
		int begin = r.rIndex;

		Object[] csts = this.cst;
		int len = constants.size();

		int twoPass = -1;
		int i = 0;
		while (i < len) {
			Constant c;
			try {
				c = readConstant(r, csts, i, utfNow);
			} catch (ClassCastException e) {
				int line = e.getStackTrace()[0].getLineNumber();
				throw new IllegalStateException("常量池存在错误 line " + line + ": " + e.getMessage());
			}
			if (c == null) c = (Constant) csts[i];

			csts[i++] = c;
			c.setIndex(i);
			if (listener != null) listener.accept(c);
			switch (c.type()) {
				case Constant.LONG:
				case Constant.DOUBLE:
					csts[i++] = CstTop.TOP;
					break;
				case Constant.METHOD_HANDLE:
					if (twoPass < 0) twoPass = i-1;
					break;
			}
		}

		if (twoPass >= 0) {
			i = twoPass;
			while (i < len) {
				Constant c = (Constant) csts[i++];
				if (c.type() == Constant.METHOD_HANDLE) {
					CstMethodHandle mh = (CstMethodHandle) c;
					mh.setRef((CstRef) csts[mh.getRefIndex()-1]);
				}
			}
		}

		this.length = r.rIndex - begin;
		this.cst = null;
	}

	public void readName(DynByteBuf r) {
		Object[] csts = this.cst;
		int len = constants.size();

		int i = 0;
		while (i < len) {
			Constant c;
			next:
			try {
				switch (r.get(r.rIndex)) {
					case Constant.UTF:
					case Constant.CLASS:
						c = readConstant(r, csts, i, false);
						break next;
					case Constant.INT:
					case Constant.FLOAT:
					case Constant.NAME_AND_TYPE:
					case Constant.FIELD:
					case Constant.METHOD:
					case Constant.INTERFACE:
					case Constant.DYNAMIC:
					case Constant.INVOKE_DYNAMIC:
						r.rIndex += 5;
						i++;
						break;
					case Constant.LONG:
					case Constant.DOUBLE:
						r.rIndex += 9;
						i += 2;
						break;
					case Constant.METHOD_TYPE:
					case Constant.MODULE:
					case Constant.PACKAGE:
					case Constant.STRING:
						r.rIndex += 3;
						i++;
						break;
					case Constant.METHOD_HANDLE:
						r.rIndex += 4;
						i++;
						break;
					default: throw new IllegalStateException("Unknown " + r);
				}
				continue;
			} catch (ClassCastException e) {
				int line = e.getStackTrace()[0].getLineNumber();
				throw new IllegalStateException("常量池存在错误 line " + line + ": " + e.getMessage());
			}
			if (c == null) c = (Constant) csts[i];

			csts[i++] = c;
			c.setIndex(i);
			if (listener != null) listener.accept(c);
			switch (c.type()) {
				case Constant.LONG:
				case Constant.DOUBLE:
					i++;
					break;
			}
		}

		this.cst = null;
	}

	static Constant readConstant(DynByteBuf r, Object[] arr, int i, boolean parseUTF) {
		int b = r.readUnsignedByte();
		switch (b) {
			case Constant.UTF: {
				Object data = parseUTF ? r.readUTF() : r.readBytes(r.readUnsignedShort());
				if (arr[i] != null) {
					try {
						((CstUTF) arr[i]).i_setData(data);
					} catch (Exception e) {
						typeError(i, b, arr[i]);
					}
					return null;
				}
				return new CstUTF(data);
			}
			case Constant.INT: return new CstInt(r.readInt());
			case Constant.FLOAT: return new CstFloat(r.readFloat());
			case Constant.LONG: return new CstLong(r.readLong());
			case Constant.DOUBLE: return new CstDouble(r.readDouble());

			case Constant.METHOD_TYPE:
			case Constant.MODULE:
			case Constant.PACKAGE:
			case Constant.CLASS:
			case Constant.STRING: {
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
					case Constant.METHOD_TYPE: return new CstMethodType(utf);
					case Constant.MODULE: return new CstModule(utf);
					case Constant.PACKAGE: return new CstPackage(utf);
					case Constant.CLASS: return new CstClass(utf);
					default: return new CstString(utf);
				}
			}
			case Constant.NAME_AND_TYPE: {
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
						nat.setName(name);
						nat.setType(type);
					} catch (Exception e) {
						typeError(i, b, arr[i]);
					}
					return null;
				}

				return new CstNameAndType(name, type);
			}
			case Constant.FIELD:
			case Constant.METHOD:
			case Constant.INTERFACE: {
				int id = r.readUnsignedShort()-1;
				CstClass clz;
				if (arr[id] == null) arr[id] = clz = new CstClass();
				else clz = (CstClass) arr[id];

				CstNameAndType nat;
				id = r.readUnsignedShort()-1;
				if (arr[id] == null) arr[id] = nat = new CstNameAndType();
				else nat = (CstNameAndType) arr[id];

				switch (b) {
					case Constant.FIELD: return new CstRefField(clz, nat);
					case Constant.METHOD: return new CstRefMethod(clz, nat);
					default: return new CstRefItf(clz, nat);
				}
			}
			case Constant.DYNAMIC:
			case Constant.INVOKE_DYNAMIC: {
				int id = r.readUnsignedShort(r.rIndex + 2)-1;
				CstNameAndType desc;
				if (arr[id] == null) arr[id] = desc = new CstNameAndType();
				else desc = (CstNameAndType) arr[id];

				CstDynamic dyn = new CstDynamic(b == Constant.INVOKE_DYNAMIC, r.readUnsignedShort(), desc);
				r.rIndex += 2;
				return dyn;
			}
			case Constant.METHOD_HANDLE: return new CstMethodHandle(r.readByte(), r.readUnsignedShort());
			default: throw new IllegalArgumentException("无效的常量类型 " + b);
		}
	}

	private static void typeError(int i, int b, Object o) {
		throw new IllegalArgumentException("pool["+i+"]的常量类型不匹配: 期待: " + Constant.toString(b) + " 得到: " + o);
	}

	public List<Constant> array() { return constants; }
	public Constant array(int i) { return i-- == 0 ? null : constants.get(i); }
	public Constant get(DynByteBuf r) {
		int id = r.readUnsignedShort();
		return id-- == 0 ? null : constants.get(id);
	}
	public String getName(DynByteBuf r) {
		int id = r.readUnsignedShort()-1;
		return id < 0 ? null : ((CstClass) constants.get(id)).getValue().getString();
	}

	private final CstTop fp = new CstTop();

	private void initRefMap() {
		if (!refMap.isEmpty()) return;
		Object[] cst = constants.getRawArray();
		for (int i = 0; i < constants.size(); i++) {
			Constant c = (Constant) cst[i];
			if (c == CstTop.TOP) continue;
			if (c != (c = refMap.intern(c))) {
				c.setIndex(c.getIndex());
			}
		}
	}

	public void setUTFValue(CstUTF utf, String value) {
		initRefMap();
		if (!refMap.remove(utf))
			throw new IllegalStateException("not able to remove " + utf);
		utf.setString(value);
		refMap.add(utf);
	}

	private void addConstant(Constant c) {
		c.setIndex(constants.size()+1);
		refMap.add(c);
		constants.add(c);

		switch (c.type()) {
			case Constant.UTF: length += 3 + DynByteBuf.byteCountDioUTF(((CstUTF) c).getString()); break;
			case Constant.INT: case Constant.FLOAT:
			case Constant.NAME_AND_TYPE: case Constant.INVOKE_DYNAMIC:
			case Constant.METHOD: case Constant.FIELD: case Constant.INTERFACE:
				length += 5; break;
			case Constant.LONG: case Constant.DOUBLE:
				length += 9;
				constants.add(CstTop.TOP);
				break;
			case Constant.METHOD_TYPE:
			case Constant.STRING: case Constant.CLASS:
			case Constant.MODULE: case Constant.PACKAGE:
				length += 3;
				break;
			case Constant.METHOD_HANDLE: length += 4; break;
			case Constant._TOP_: break;
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
		} else if (!msg.equals((utf = (CstUTF) o).getString())) {
			throw new IllegalStateException("Unfit utf id!!! G: '" + utf.getString() + "' E: '" + msg + '\'');
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
			t.setName(uName);
			t.setType(uType);
			addConstant(t);
			ref = t;
		}

		return (CstNameAndType) ref;
	}
	public int getDescId(String name, String desc) { return getDesc(name, desc).getIndex(); }

	public CstClass getClazz(String name) {
		CstUTF uName = getUtf(name);

		Object ref = refMap.find(fp.set(Constant.CLASS, uName));
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

		Object ref = refMap.find(fp.set(Constant.METHOD, clazz, nat));
		if (ref == fp) {
			CstRefMethod t = new CstRefMethod();
			t.setClazz(clazz);
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

		Object ref = refMap.find(fp.set(Constant.FIELD, clazz, nat));
		if (ref == fp) {
			CstRefField t = new CstRefField();
			t.setClazz(clazz);
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

		Object ref = refMap.find(fp.set(Constant.INTERFACE, clazz, nat));
		if (ref == fp) {
			CstRefItf t = new CstRefItf();
			t.setClazz(clazz);
			t.desc(nat);
			addConstant(t);
			ref = t;
		}

		return (CstRefItf) ref;
	}
	public int getItfRefId(String owner, String name, String desc) { return getItfRef(owner, name, desc).getIndex(); }

	private CstRef getRefByType(String owner, String name, String desc, byte type) {
		switch (type) {
			case Constant.FIELD: return getFieldRef(owner, name, desc);
			case Constant.METHOD: return getMethodRef(owner, name, desc);
			default: // no default branch
			case Constant.INTERFACE: return getItfRef(owner, name, desc);
		}
	}

	public CstMethodHandle getMethodHandle(String owner, String name, String desc, byte kind, byte type) {
		CstRef ref = getRefByType(owner, name, desc, type);

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

		Object ref = refMap.find(fp.set(Constant.PACKAGE, name));
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

		Object ref = refMap.find(fp.set(Constant.MODULE, name));
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
		if (c == null) throw new NullPointerException("Check null before reset()!");
		switch (c.type()) {
			case Constant.DYNAMIC:
			case Constant.INVOKE_DYNAMIC: {
				CstDynamic dyn = (CstDynamic) c;
				dyn.setDesc(reset(dyn.desc()));
			}
			break;
			case Constant.CLASS:
			case Constant.STRING:
			case Constant.METHOD_TYPE:
			case Constant.MODULE:
			case Constant.PACKAGE: {
				CstRefUTF ref = (CstRefUTF) c;
				ref.setValue(reset(ref.getValue()));
			}
			break;
			case Constant.METHOD_HANDLE: {
				CstMethodHandle ref = ((CstMethodHandle) c);
				ref.setRef(reset(ref.getRef()));
			}
			break;
			case Constant.METHOD:
			case Constant.INTERFACE:
			case Constant.FIELD: {
				CstRef ref = (CstRef) c;
				ref.setClazz(reset(ref.getClazz()));
				ref.desc(reset(ref.desc()));
			}
			break;
			case Constant.NAME_AND_TYPE: {
				CstNameAndType nat = (CstNameAndType) c;
				nat.setName(reset(nat.getName()));
				nat.setType(reset(nat.getType()));
			}
			break;
			case Constant.INT:
			case Constant.DOUBLE:
			case Constant.FLOAT:
			case Constant.LONG:
			case Constant.UTF:
				// No need to do anything, just append it
				break;
			default: throw new IllegalArgumentException("Unsupported type: " + c.type());
		}

		int id = c.getIndex()-1;
		if (id >= 0 && id < constants.size() && constants.getRawArray()[id] == c) {
			return c;
		}

		initRefMap();
		T t = (T) refMap.find(c);
		if (t != c) return t;

		addConstant(c);
		return c;
	}

	public void lightCopy(ConstantPool pool) {
		this.constants = pool.constants;
		this.cst = pool.cst;
		this.refMap = pool.refMap;
		this.length = pool.length;

		if (listener != null) listener.accept(null);
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
		return TextUtil.prettyTable(new StringBuilder("constants[" + constants.size() + "]=["), " ", " ", "    ", array).append("]").toString();
	}

	public void clear() {
		this.constants.clear();
		this.refMap.clear();
		this.length = 0;
		if (listener != null) listener.accept(null);
	}

	Consumer<Constant> listener;

	public void setAddListener(Consumer<Constant> listener) {
		this.listener = listener;
	}

	public int byteLength() {
		if (length == 0) throw new IllegalStateException("This pool is not ready to write");
		return length;
	}
}