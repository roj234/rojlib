package roj.mapper;

import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/2/22 0022 21:57
 */
public abstract class ParamNameMapper {
	public static List<String> getParameterName(ConstantPool cp, MethodNode m) {
		int j = (m.modifier() & AccessFlag.STATIC) == 0 ? 1 : 0;

		int len = TypeHelper.paramSize(m.rawDesc())+j;
		SimpleList<String> names = new SimpleList<>(len);
		names.i_setSize(len);

		ParamNameMapper pmap = new ParamNameMapper() {
			@Override
			protected List<String> getNewParamName(MethodNode m) {
				return names;
			}
		};

		if (pmap.mapParam(cp, m)) {
			List<Type> parameters = m.parameters();
			for (int i = 0; i < parameters.size(); i++) {
				if (j != i) names.set(i, names.get(j));
				j += parameters.get(i).length();
			}
			names.i_setSize(parameters.size());
			return names;
		}

		return null;
	}

	public static final MyBitSet HUMAN_READABLE_TOKENS = MyBitSet.from("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$");

	public MyBitSet validNameChars = HUMAN_READABLE_TOKENS;

	public boolean mapParam(ConstantPool pool, MethodNode m) {
		List<String> parNames = getNewParamName(m);

		AttrUnknown a;
		if (!parNames.isEmpty()) {
			a = (AttrUnknown) m.attrByName("MethodParameters");
			if (a != null) {
				int i = 0;
				int j = (m.modifier() & AccessFlag.STATIC) == 0 ? 1 : 0;
				List<Type> parameters = m.parameters();

				DynByteBuf r = Parser.reader(a);
				int len = r.readUnsignedByte();
				while (len-- > 0) {
					String name = ((CstUTF) pool.get(r)).str();
					if (parNames.get(j) == null && validNameChars.contains(name.charAt(0))) parNames.set(j, name);

					r.rIndex += 2;

					j += parameters.get(i++).length();
				}
			}
		}

		AttrUnknown code = (AttrUnknown) m.attrByName("Code");
		if (code != null) {
			MyBitSet replaced = new MyBitSet(parNames.size());

			DynByteBuf r = Parser.reader(code);
			r.rIndex += 4; // stack size
			int codeLen = r.readInt();
			r.rIndex += codeLen; // code

			int len = r.readUnsignedShort(); // exception
			r.rIndex += len << 3;

			int attrLen = r.rIndex;
			len = r.readUnsignedShort();

			while (len-- > 0) {
				String aname = ((CstUTF) pool.get(r)).str();
				int end = r.readInt() + r.rIndex;
				switch (aname) {
					case "LocalVariableTable":
						List<V> list = readVar(pool, r);
						for (int j = 0; j < list.size(); j++) {
							V entry = list.get(j);

							String ref = mapType(entry.type.str());
							if (ref != null) pool.setUTFValue(entry.type, ref);

							String name = entry.name.str();
							if (entry.start == 0 && parNames.size() > entry.slot) {
								replaced.add(entry.slot);

								String n = parNames.get(entry.slot);
								if (n != null) {
									if (!n.equals(name)) {
										r.putShort(entry.nameOff, pool.getUtfId(n));
										continue;
									}
								} else {
									parNames.set(entry.slot, entry.name.str());
								}
							}

							if (name.isEmpty() || !validNameChars.contains(name.charAt(0))) {
								r.putShort(entry.nameOff, pool.getUtfId("lvt"+entry.start+"_"+entry.slot));
							}
						}
						break;
					case "LocalVariableTypeTable":
						list = readVar(pool, r);
						for (int j = 0; j < list.size(); j++) {
							V entry = list.get(j);

							String ref = mapGeneric(entry.type.str());
							if (ref != null) pool.setUTFValue(entry.type, ref);

							String name = entry.name.str();
							if (entry.start == 0 && parNames.size() > entry.slot) {
								replaced.add(entry.slot);

								String n = parNames.get(entry.slot);
								if (n != null) {
									if (!n.equals(name)) {
										r.putShort(entry.nameOff, pool.getUtfId(n));
										continue;
									}
								} else {
									parNames.set(entry.slot, entry.name.str());
								}
							}

							if (name.isEmpty() || !validNameChars.contains(name.charAt(0))) {
								r.putShort(entry.nameOff, pool.getUtfId("lvt"+entry.start+"_"+entry.slot));
							}
						}
						break;
				}
				r.rIndex = end;
			}

			if (replaced.size() > parNames.size()) {
				System.out.println("catch " + replaced);
				System.out.println(parNames);
				return false;
			}
		}

		add:
		if (!parNames.isEmpty()) {
			int i = 0;
			int j = (m.modifier() & AccessFlag.STATIC) == 0 ? 1 : 0;

			List<Type> parameters = m.parameters();
			ByteList attr = IOUtil.getSharedByteBuf().put((byte) 0);

			while (j < parNames.size()) {
				if (i == parameters.size()) {
					// Static flag not match or Error happens
					break add;
				}

				// including Null check
				String name = parNames.get(j);
				if (name == null) {
					name = fallbackParamName(m, i, j);
					parNames.set(j, name);
				}
				attr.putShort(pool.getUtfId(name)).putShort(0);

				j += parameters.get(i++).length();
			}
			attr.put(0, (byte) i);

			m.putAttr(new AttrUnknown("MethodParameters", ByteList.wrap(attr.toByteArray())));
			return true;
		}
		return false;
	}

	public static List<V> readVar(ConstantPool cp, DynByteBuf r) {
		int len = r.readUnsignedShort();
		List<V> list = new SimpleList<>(len);

		for (int i = 0; i < len; i++) {
			V v = new V();
			v.start = r.readUnsignedShort();
			v.end = r.readUnsignedShort();
			v.nameOff = r.rIndex;
			v.name = ((CstUTF) cp.get(r));
			v.type = ((CstUTF) cp.get(r));
			v.slot = r.readUnsignedShort();
			list.add(v);
		}
		return list;
	}

	public static final class V {
		public CstUTF name, type;
		public int slot, nameOff;
		public int start, end;

		public final void write(DynByteBuf w) {
			w.putShort(start).putShort(end).putShort(name.getIndex()).putShort(type.getIndex()).putShort(slot);
		}
	}

	protected String mapType(String string) {
		return null;
	}
	protected String mapGeneric(String string) {
		return null;
	}

	protected String fallbackParamName(MethodNode m, int index, int stackPos) {
		return "par"+index;
	}
	protected List<String> getNewParamName(MethodNode m) {
		return Collections.emptyList();
	}
}
