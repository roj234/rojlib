package roj.asmx.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.attr.AttrUnknown;
import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/2/22 0022 21:57
 */
public abstract class ParamNameMapper {
	public static List<String> getParameterName(ConstantPool cp, MethodNode m) {
		int j = (m.modifier() & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

		int len = TypeHelper.paramSize(m.rawDesc())+j;
		SimpleList<String> names = new SimpleList<>(len);
		names._setSize(len);

		ParamNameMapper pmap = new ParamNameMapper() {
			@Override
			protected List<String> getParamNames(MethodNode m) {
				return names;
			}
		};

		if (pmap.mapParam(cp, m)) {
			List<Type> parameters = m.parameters();
			for (int i = 0; i < parameters.size(); i++) {
				if (j != i) names.set(i, names.get(j));
				j += parameters.get(i).length();
			}
			names._setSize(parameters.size());
			return names;
		}

		return null;
	}

	public static final MyBitSet HUMAN_READABLE_TOKENS = MyBitSet.from("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz_$");

	public MyBitSet validNameChars = HUMAN_READABLE_TOKENS;

	public boolean mapParam(ConstantPool pool, MethodNode m) {
		List<String> parNames = getParamNames(m);

		Attribute a;
		DynByteBuf r;
		if (!parNames.isEmpty()) {
			a = m.attrByName("MethodParameters");
			if (a != null) {
				int i = 0;
				int j = (m.modifier() & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
				List<Type> parameters = m.parameters();

				r = a instanceof AttrUnknown ? Parser.reader(a) : AttrUnknown.downgrade(pool, IOUtil.getSharedByteBuf(), a).getRawData();
				int len = r.readUnsignedByte();
				while (len-- > 0) {
					String name = ((CstUTF) pool.get(r)).str();
					while (parNames.size() < j) parNames.add(null);
					if (parNames.get(j) == null && validNameChars.contains(name.charAt(0))) parNames.set(j, name);

					r.rIndex += 2;

					j += parameters.get(i++).length();
				}
			}
		}

		a =  m.attrByName("Code");
		if (a != null) {
			MyBitSet replaced = new MyBitSet(parNames.size());

			r = a instanceof AttrUnknown ? Parser.reader(a) : AttrUnknown.downgrade(pool, IOUtil.getSharedByteBuf(), a).getRawData();
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
						List<V> list = parseLVT(pool, r);
						for (int j = 0; j < list.size(); j++) {
							V entry = list.get(j);

							String ref = mapType(entry.type.str());
							if (ref != null) r.putShort(entry.typeOff, pool.getUtfId(ref));

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
						list = parseLVT(pool, r);
						for (int j = 0; j < list.size(); j++) {
							V entry = list.get(j);

							String ref = mapGenericType(entry.type.str());
							if (ref != null) r.putShort(entry.typeOff, pool.getUtfId(ref));

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
			int j = (m.modifier() & Opcodes.ACC_STATIC) == 0 ? 1 : 0;

			List<Type> parameters = m.parameters();
			ByteList attr = IOUtil.getSharedByteBuf().put(0);

			while (j < parNames.size()) {
				if (i == parameters.size()) {
					// Static flag not match or Error happens
					break add;
				}

				// including Null check
				String name = parNames.get(j);
				if (name == null) {
					name = getFallbackParamName(m, i, j);
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

	private static final class V {
		CstUTF name, type;
		int slot, nameOff, typeOff;
		int start, end;
	}
	private static List<V> parseLVT(ConstantPool cp, DynByteBuf r) {
		int len = r.readUnsignedShort();
		List<V> list = new SimpleList<>(len);

		for (int i = 0; i < len; i++) {
			V v = new V();
			v.start = r.readUnsignedShort();
			v.end = r.readUnsignedShort();
			v.nameOff = r.rIndex;
			v.name = ((CstUTF) cp.get(r));
			v.typeOff = r.rIndex;
			v.type = ((CstUTF) cp.get(r));
			v.slot = r.readUnsignedShort();
			list.add(v);
		}
		return list;
	}


	@Nullable protected String mapType(String type) {return null;}
	@Nullable protected String mapGenericType(String type) {return null;}

	protected String getFallbackParamName(MethodNode m, int index, int stackPos) {return "arg"+index;}
	protected abstract List<String> getParamNames(MethodNode m);
}