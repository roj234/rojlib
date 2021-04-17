package roj.mapper;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrRecord;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Signature;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.collect.MyBitSet;
import roj.io.IOUtil;
import roj.mapper.util.Desc;
import roj.text.CharList;
import roj.util.DynByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import static roj.asm.tree.anno.AnnVal.*;
import static roj.asm.type.Type.*;

/**
 * 修改class名字
 *
 * @author Roj234
 * @since 2021/6/18 9:51
 * // todo: 高版本的新增属性的处理
 */
public final class CodeMapper extends Mapping {
	private Map<Desc, List<String>> paramNameMap;
	private ConstMapper libraryInfo;
	private final ParMap pm = new ParMap();
	private class ParMap extends ParamNameMapper {
		@Override
		protected List<String> getNewParamName(MethodNode m) {
			if (paramNameMap != null) {
				MapUtil u = MapUtil.getInstance();

				Desc md = u.sharedDC;
				md.owner = classMap.getOrDefault(m.ownerClass(), m.ownerClass());
				md.name = m.name();
				md.param = m.rawDesc();

				List<String> parents = libraryInfo.selfSupers.getOrDefault(m.ownerClass(), Collections.emptyList());
				int i = 0;
				do {
					List<String> param = paramNameMap.get(md);
					if (param != null) return param;

					if (i == parents.size()) break;
					md.owner = parents.get(i++);
				} while (true);
			}
			return Collections.emptyList();
		}

		@Override
		protected String mapType(String type) {
			return MapUtil.getInstance().mapFieldType(classMap, type);
		}

		@Override
		protected String mapGeneric(String type) {
			Signature sign = Signature.parse(type);
			sign.rename(NAME_REMAPPER);
			return sign.toDesc();
		}
	}

	private final UnaryOperator<String> NAME_REMAPPER = (old) -> {
		String now = MapUtil.getInstance().mapOwner(classMap, old, false);
		return now == null ? old : now;
	};

	public CodeMapper(boolean checkFieldType) {
		super(checkFieldType);
	}

	public CodeMapper(CodeMapper o) {
		super(o);
		this.paramNameMap = o.paramNameMap;
		this.pm.validNameChars = o.pm.validNameChars;
	}

	public CodeMapper(Mapping mapping) {
		super(mapping);
		if (mapping instanceof ConstMapper) {
			libraryInfo = (ConstMapper) mapping;
		}
	}

	public void setValidVarChars(MyBitSet valid) {
		pm.validNameChars = valid == null ? ParamNameMapper.HUMAN_READABLE_TOKENS : valid;
	}

	public final void remap(boolean singleThread, List<Context> arr) {
		if (singleThread || arr.size() <= 1000) {
			Context cur = null;

			try {
				for (Context entry : arr) {
					cur = entry;
					processOne(entry);
				}
			} catch (Throwable e) {
				throw new RuntimeException("At parsing " + cur, e);
			}
		} else {
			List<List<Context>> splatted = new ArrayList<>(arr.size() / 1000 + 1);

			int i = 0;
			while (i < arr.size()) {
				int cnt = Math.min(arr.size() - i, 1000);
				splatted.add(arr.subList(i, i + cnt));
				i += cnt;
			}

			MapUtil.async(this::processOne, splatted);
		}
	}

	// 致天天想着搞优化的我: 有顺序！！！
	public final void processOne(Context ctx) {
		MapUtil U = MapUtil.getInstance();
		ConstantData data = ctx.getData();
		data.normalize();

		// 这里都成了String，要第一个！
		List<InnerClasses.InnerClass> classes = AttrHelper.getInnerClasses(data.cp, data);
		if (classes != null) {
			CharList sb = IOUtil.getSharedCharBuf();
			for (int j = 0; j < classes.size(); j++) {
				InnerClasses.InnerClass clz = classes.get(j);
				if (clz.name != null && clz.parent != null) {
					sb.clear();
					String name = U.mapOwner(classMap, sb.append(clz.parent).append('$').append(clz.name), false);
					if (name != null) {
						int i = name.lastIndexOf('$');
						if (i == -1) {
							System.out.println("CodeMapper.java:145: No '$' sig: " + clz.parent + '$' + clz.name + " => " + name);
							clz.name = name;
							name = U.mapOwner(classMap, clz.parent, false);
							if (name != null) clz.parent = name;
						} else {
							clz.name = name.substring(i + 1);
							clz.parent = name.substring(0, i);
						}
					}
				}
				if (clz.self != null) {
					String name = U.mapOwner(classMap, clz.self, false);
					if (name != null) clz.self = name;
				}
			}
		}

		BootstrapMethods bs = data.parsedAttr(data.cp,Attribute.BootstrapMethods);
		if (bs != null) {
			List<BootstrapMethods.BootstrapMethod> methods = bs.methods;
			for (int i = 0; i < methods.size(); i++) {
				BootstrapMethods.BootstrapMethod ibm = methods.get(i);
				List<Constant> args = ibm.arguments;
				for (int j = 0; j < args.size(); j++) {
					Constant cst = args.get(j);
					if (cst.type() == Constant.METHOD_TYPE) {
						CstMethodType type = (CstMethodType) cst;

						String oldCls = type.name().str();
						String newCls = U.mapMethodParam(classMap, oldCls);

						if (!oldCls.equals(newCls)) {
							type.setValue(data.cp.getUtf(newCls));
						}
					}
				}
			}
		}

		// 泛型的UTF(几乎)不可能重复，但真碰到了我倒霉，还是放前面，至于和BSM重复？滚蛋
		mapSignature(data.cp, data);

		mapParam(U, ctx, data);

		// And, F Annotation (For Mixin)
		mapAnnotations(U, data.cp, data);

		// 何时把这个忘了？？？
		mapClassAndSuper(U, data);

		mapRecord(U.sharedDC, data);

		// 以后可能会有attribute序列化的需求，省得忘了
		data.normalize();
	}

	private void mapRecord(Desc sp, ConstantData data) {
		Attribute attr = data.attrByName("Record");
		if (attr == null) return;

		AttrRecord r;
		if (attr instanceof AttrRecord) r = (AttrRecord) attr;
		else data.putAttr(r = new AttrRecord(attr.getRawData(), data.cp));

		List<AttrRecord.Val> methods = r.variables;
		for (int i = 0; i < methods.size(); i++) {
			AttrRecord.Val ibm = methods.get(i);

			sp.owner = data.name;
			sp.name = ibm.name;
			sp.param = checkFieldType ? ibm.type : "";

			String newName = fieldMap.get(sp);
			if (newName != null) {
				// System.out.println("[25R-" + data.name + "]: " + sp.owner + '.' + sp.name + " => " + newName);
				ibm.name = newName;
			}
		}
	}

	private void mapAnnotations(MapUtil U, ConstantPool pool, Attributed list) {
		AsmShared ash = AsmShared.local();

		Attribute a = list.attrByName("RuntimeVisibleAnnotations");
		if (a != null) mapAnnotation(U, pool, ash.copy(a.getRawData()));
		a = list.attrByName("RuntimeInvisibleAnnotations");
		if (a != null) mapAnnotation(U, pool, ash.copy(a.getRawData()));
	}

	private void mapAnnotation(MapUtil U, ConstantPool pool, DynByteBuf r) {
		int len = r.readChar();
		while (len-- > 0) {
			CstUTF owner = (CstUTF) pool.get(r);
			String newOwner = U.mapFieldType(classMap, owner.str());
			if (newOwner != null) {
				r.putShort(r.rIndex-2, pool.getUtfId(newOwner));
			}
			int valCnt = r.readUnsignedShort();
			while (valCnt-- > 0) {
				r.rIndex += 2;
				mapAnnotationNode(U, pool, r);
			}
		}
	}

	private void mapAnnotationNode(MapUtil U, ConstantPool cp, DynByteBuf r) {
		switch (r.readUnsignedByte()) {
			case BOOLEAN:
			case BYTE:
			case SHORT:
			case CHAR:
			case INT:
			case DOUBLE:
			case FLOAT:
			case LONG:
			case STRING:
				r.rIndex += 2;
				break;
			case ANNOTATION_CLASS: {
				CstUTF owner = (CstUTF) cp.get(r);
				String newOwner = U.mapFieldType(classMap, owner.str());
				if (newOwner != null) {
					r.putShort(r.rIndex-2, cp.getUtfId(newOwner));
				}
			}
			break;
			case ENUM: {
				CstUTF owner = (CstUTF) cp.get(r);
				CstUTF name = (CstUTF) cp.get(r);

				if (checkFieldType) {
					System.out.println("[Warn]checkFieldType is not compatible with @Interface.Enum type");
				}

				Desc fd = U.sharedDC;
				String prevOwner = fd.owner;

				fd.owner = owner.str();
				fd.name = name.str();
				fd.param = "";

				String newName = fieldMap.get(fd);
				if (newName != null) {
					System.out.println("[ANN] E " + fd + " => " + newName);
					r.putShort(r.rIndex - 2, cp.getUtfId(newName));
				}
				String newOwner = classMap.get(fd.owner);
				if (newOwner != null) {
					System.out.println("[ANN] E " + owner.str() + " => " + newOwner);
					cp.setUTFValue(owner, newOwner);
				}

				fd.owner = prevOwner;
			}
			break;
			case ANNOTATION: {
				r.rIndex += 2;
				int len = r.readUnsignedShort();
				while (len-- > 0) {
					r.rIndex += 2;
					mapAnnotationNode(U, cp, r);
				}
			}
			break;
			case ARRAY:
				int len = r.readUnsignedShort();
				while (len-- > 0) {
					mapAnnotationNode(U, cp, r);
				}
				break;
		}
	}

	private void mapSignature(ConstantPool pool, Attributed list) {
		Attribute a = list.attrByName("Signature");
		if (a == null) return;

		Signature generic = a instanceof Signature ? (Signature) a : Signature.parse(((CstUTF) pool.array(Parser.reader(a).readUnsignedShort())).str());
		generic.rename(NAME_REMAPPER);
		list.putAttr(generic);
	}

	private void mapClassAndSuper(MapUtil U, ConstantData data) {
		String name = U.mapOwner(classMap, data.name, false);
		// side effect: changes MethodNode.ownerClass()
		if (name != null) data.name(name);

		if (data.parent != null) {
			name = U.mapOwner(classMap, data.parent, false);
			if (name != null) data.parent(name);
		}

		List<CstClass> itf = data.interfaces;
		for (int i = 0; i < itf.size(); i++) {
			CstClass clz = itf.get(i);
			name = U.mapOwner(classMap, clz.name().str(), false);
			if (name != null) clz.setValue(data.cp.getUtf(name));
		}
	}

	private void mapParam(MapUtil U, Context ctx, ConstantData data) {
		String oldCls, newCls;
		int i;

		List<? extends MethodNode> methods1 = data.methods;
		Desc md = U.sharedDC;
		md.owner = data.name;
		for (i = 0; i < methods1.size(); i++) {
			RawMethod method = (RawMethod) methods1.get(i);

			/**
			 * Method Name
			 */
			md.name = method.name.str();
			md.param = method.type.str();

			String newName = methodMap.get(md);
			if (newName != null) {
				method.name = data.cp.getUtf(newName);
			}

			/**
			 * Method Parameters
			 */
			oldCls = method.type.str();
			newCls = U.mapMethodParam(classMap, oldCls);

			if (!oldCls.equals(newCls)) {
				method.type = data.cp.getUtf(newCls);
			}

			mapSignature(data.cp, method);
			mapAnnotations(U, data.cp, method);
		}

		md.owner = data.name;
		md.param = "";
		List<? extends FieldNode> fields = data.fields;
		for (i = 0; i < fields.size(); i++) {
			RawField field = (RawField) fields.get(i);

			/**
			 * Field Name
			 */
			md.name = field.name.str();
			if (checkFieldType) md.param = field.type.str();

			String newName = fieldMap.get(md);
			if (newName != null) {
				field.name = data.cp.getUtf(newName);
			}

			/**
			 * Field Type
			 */
			oldCls = field.type.str();
			newCls = U.mapFieldType(classMap, oldCls);

			if (newCls != null) {
				field.type = data.cp.getUtf(newCls);
			}

			mapSignature(data.cp, field);
			mapAnnotations(U, data.cp, field);
		}

		// 十分不幸的是, field rename (when parameterized) 会被 LVT 工序影响
		for (i = 0; i < methods1.size(); i++) {
			pm.mapParam(data.cp, methods1.get(i));
		}

		List<Constant> list = data.cp.array();
		for (int j = 0; j < list.size(); j++) {
			Constant c = list.get(j);
			switch (c.type()) {
				case Constant.INTERFACE:
				case Constant.METHOD: {
					CstRef method = (CstRef) c;

					/**
					 * 修改{@link CstRefMethod}方法调用
					 */
					oldCls = method.descType();
					newCls = U.mapMethodParam(classMap, oldCls);

					if (!newCls.equals(oldCls)) {
						data.cp.setUTFValue(method.desc().getType(), newCls);
					}
				}
				break;
				case Constant.CLASS: {
					CstClass clazz = (CstClass) c;
					/**
					 * 修改class名字
					 */
					oldCls = clazz.name().str();
					newCls = U.mapOwner(classMap, oldCls, false);
					if (newCls != null) {
						clazz.setValue(data.cp.getUtf(newCls));
					}
				}
				break;
				case Constant.FIELD: {
					CstRef field = (CstRef) c;

					/**
					 * 修改{@link CstRefField}字段类型
					 */
					oldCls = field.descType();
					newCls = U.mapFieldType(classMap, oldCls);

					if (newCls != null) {
						field.desc().setType(data.cp.getUtf(newCls));
					}
				}
				break;
				case Constant.INVOKE_DYNAMIC: {
					CstDynamic dyn = (CstDynamic) c;
					/**
					 * Lambda方法的参数
					 */
					oldCls = dyn.desc().getType().str();
					newCls = U.mapMethodParam(classMap, oldCls);

					if (!oldCls.equals(newCls)) {
						data.cp.setUTFValue(dyn.desc().getType(), newCls);
					}
				}
				break;
			}
		}
	}

	/**
	 * By slot
	 * Caution: unmapped class name + mapped method name / descriptor
	 */
	public final void setParamMap(Map<Desc, List<String>> paramMap) {
		this.paramNameMap = paramMap;
	}
}