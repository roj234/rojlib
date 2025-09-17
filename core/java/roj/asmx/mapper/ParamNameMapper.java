package roj.asmx.mapper;

import org.jetbrains.annotations.Nullable;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/2/22 21:57
 */
public abstract class ParamNameMapper {
	public static List<@Nullable String> getParameterNames(ConstantPool cp, MethodNode m) {
		int len = m.parameters().size();
		ArrayList<String> names = new ArrayList<>(len);
		names._setSize(len);

		ParamNameMapper pmap = new ParamNameMapper() {
			@Override
			protected List<String> getParamNames(MethodNode m) {return names;}
		};

		return pmap.mapParam(cp, m) ? names : null;

	}

	public static final Predicate<String> IS_VALID = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]+").asMatchPredicate();
	public Predicate<String> isValid = IS_VALID;

	protected List<String> copyOf(List<String> data) {return new ArrayList<>(data);}

	public boolean mapParam(ConstantPool pool, MethodNode m) {
		List<String> parNames = getParamNames(m);

		Attribute a;
		DynByteBuf r;
		//MethodParameters属性的参数名称与descriptor一一对应，但Signature和ParameterAnnotations并非如此
		if (!parNames.isEmpty()) {
			a = m.getAttribute("MethodParameters");
			if (a != null) {
				r = a instanceof UnparsedAttribute ? a.getRawData() : UnparsedAttribute.serialize(pool, IOUtil.getSharedByteBuf(), a).getRawData();
				int len = r.readUnsignedByte();
				for (int i = 0; i < len; i++) {
					CstUTF namecst = (CstUTF) pool.getNullable(r);
					r.rIndex += 2;

					if (namecst != null) {
						String name = namecst.str();
						while (i >= parNames.size()) parNames.add(null);
						if (parNames.get(i) == null && isValid.test(name)) parNames.set(i, name);
					}
				}
			}
		}

		// try get from LocalVariableTable
		a = m.getAttribute("Code");
		if (a != null) {
			int slotBegin = (m.modifier() & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
			int slot = slotBegin;
			List<Type> parameters = m.parameters();
			byte[] slotToArg = new byte[parameters.size() << 1];
			for (int i = 0; i < parameters.size(); i++) {
				slotToArg[slot] = (byte) (i+1);
				slot += parameters.get(i).length();
			}

			r = a instanceof UnparsedAttribute ? a.getRawData().slice() : UnparsedAttribute.serialize(pool, IOUtil.getSharedByteBuf(), a).getRawData();
			r.rIndex += 4; // stack size
			int codeLen = r.readInt();
			r.rIndex += codeLen; // code

			int len = r.readUnsignedShort(); // exception
			r.rIndex += len << 3;

			len = r.readUnsignedShort();

			while (len-- > 0) {
				String aname = ((CstUTF) pool.get(r)).str();
				int end = r.readInt() + r.rIndex;
				switch (aname) {
					case "LocalVariableTable" -> {
						var variables = parseLVT(pool, r);
						for (int j = 0; j < variables.size(); j++) {
							V entry = variables.get(j);

							String ref = mapType(entry.type.str());
							if (ref != null) r.setShort(entry.typeOff, pool.getUtfId(ref));

							String name = entry.name.str();
							if (!parNames.isEmpty() && entry.start == 0 && entry.slot >= slotBegin && entry.slot < slotToArg.length) {
								int argIndex = (slotToArg[entry.slot] & 0xFF) - 1;
								if (argIndex >= 0) {
									slotToArg[entry.slot] = 0;

									String newName = argIndex < parNames.size() ? parNames.get(argIndex) : null;
									if (newName != null) {
										if (!newName.equals(name)) {
											r.setShort(entry.nameOff, pool.getUtfId(newName));
											continue;
										}
									} else {
										if (isValid.test(name)) {
											while (argIndex >= parNames.size()) parNames.add(null);
											parNames.set(argIndex, name);
										}
									}
								}
							}

							/*if (!isValid.test(name)) {
								r.putShort(entry.nameOff, pool.getUtfId("lvt"+entry.slot+"_"+entry.start));
							}*/
						}
					}
					case "LocalVariableTypeTable" -> {
						var variables = parseLVT(pool, r);
						for (int j = 0; j < variables.size(); j++) {
							var entry = variables.get(j);
							String ref = mapGenericType(entry.type.str());
							if (ref != null) r.setShort(entry.typeOff, pool.getUtfId(ref));
						}
					}
				}
				r.rIndex = end;
			}
		}

		if (!parNames.isEmpty()) {
			ByteList data = IOUtil.getSharedByteBuf().put(parNames.size());
			for (int i = 0; i < parNames.size(); i++) {
				String name = parNames.get(i);
				data.putShort(name == null ? 0 : pool.getUtfId(name)).putShort(0);
			}
			m.addAttribute(new UnparsedAttribute("MethodParameters", data.toByteArray()));
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
		List<V> list = new ArrayList<>(len);

		for (int i = 0; i < len; i++) {
			V v = new V();
			v.start = r.readUnsignedShort();
			v.end = r.readUnsignedShort();
			v.nameOff = r.rIndex;
			v.name = cp.get(r);
			v.typeOff = r.rIndex;
			v.type = cp.get(r);
			v.slot = r.readUnsignedShort();
			list.add(v);
		}
		return list;
	}

	@Nullable protected String mapType(String type) {return null;}
	@Nullable protected String mapGenericType(String type) {return null;}

	/**
	 * 按顺序排列的参数名称，部分可以为null表示不做改变
	 * 不需要管理是否静态，或者为long预留，每"个"参数都只占用一位。
	 */
	protected abstract List<String> getParamNames(MethodNode m);
}