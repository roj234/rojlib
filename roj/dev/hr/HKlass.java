package roj.dev.hr;

import roj.asm.cst.CstClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MoFNode;
import roj.asm.type.Type;
import roj.asm.util.AccessFlag;
import roj.collect.CharMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.mapper.util.Desc;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:35
 */
public final class HKlass {
	public String parent;
	public HKlass parentKlass;

	public final MyHashSet<String> interfaces = new MyHashSet<>();

	final SimpleList<HFieldDesc> staticFields = new SimpleList<>();

	final SimpleList<Desc> fields = new SimpleList<>(), fieldSum = new SimpleList<>();
	final SimpleList<HMethodDesc> methods = new SimpleList<>();

	public final CharMap<HMethodDesc> inheritedMethodsLookup = new CharMap<>();
	final SimpleList<HMethodDesc> inheritableMethods = new SimpleList<>();
	int methodId;

	public void init(HContext ctx, ConstantData info) {
		parent = info.parent;
		interfaces.clear();
		interfaces.ensureCapacity(info.interfaces.size());
		for (CstClass c : info.interfaces) {
			interfaces.add(c.getValue().getString());
		}

		List<? extends MoFNode> nodes = info.fields;

		fields.clear();
		fieldSum.clear();
		staticFields.clear();

		for (int i = 0; i < nodes.size(); i++) {
			MoFNode field = nodes.get(i);
			if ((field.accessFlag()&AccessFlag.STATIC) != 0) {
				staticFields.add(new HFieldDesc(info.name, field.name(), field.rawDesc(), field.accessFlag()));
			} else {
				Desc e = new Desc(info.name, field.name(), field.rawDesc(), field.accessFlag());
				fields.add(e);
				fieldSum.add(e);
			}
		}

		nodes = info.methods;

		methods.clear();
		SimpleList<HMethodDesc> iMethods = inheritableMethods;
		iMethods.clear();

		for (int i = 0; i < nodes.size(); i++) {
			MoFNode method = nodes.get(i);
			HMethodDesc e = new HMethodDesc(info.name, method.name(), method.rawDesc(), method.accessFlag());
			if ((method.accessFlag()&(AccessFlag.PRIVATE|AccessFlag.STATIC|AccessFlag.FINAL)) != 0) {
				methods.add(e);
			} else {
				iMethods.add(e);
			}
		}

		inheritedMethodsLookup.clear();
		HKlass pk = parentKlass = ctx.getManagedClass(parent);
		if (pk == null) {
			methodId = iMethods.size();
			for (int i = 0; i < iMethods.size(); i++) {
				HMethodDesc desc = iMethods.get(i);
				desc.id = (char) i;
				inheritedMethodsLookup.put((char)i, desc);
			}
		} else {
			fieldSum.addAll(0, pk.fieldSum);

			int id = pk.methodId;
			for (int i = 0; i < iMethods.size(); i++) {
				HMethodDesc desc = iMethods.get(i);
				int methodId = pk.getMethodId(desc);
				if (methodId < 0) methodId = id++;
				else iMethods.remove(i--);

				desc.id = (char) methodId;
				inheritedMethodsLookup.put((char) methodId, desc);
			}
		}
	}

	public int getMethodId(Desc desc) {
		HKlass k = this;
		do {
			for (int i = 0; i < inheritableMethods.size(); i++) {
				HMethodDesc desc1 = inheritableMethods.get(i);
				if (desc1.equals(desc)) return desc1.id;
			}
			k = k.parentKlass;
		} while (k != null);
		return -1;
	}

	public HObject newInstance() {
		return new HObject(this, createFields());
	}

	private HField[] createFields() {
		HField[] field = new HField[fieldSum.size()];
		for (int i = 0; i < field.length; i++) {
			field[i] = createField(fieldSum.get(i).param.charAt(0));
		}
		return field;
	}

	private static HField createField(char type) {
		switch (type) {
			default:
			case Type.CLASS:
			case Type.ARRAY: return new HFieldL();
			case Type.BOOLEAN:
			case Type.BYTE:
			case Type.CHAR:
			case Type.SHORT: // todo
			case Type.INT: return new HFieldI();
			case Type.FLOAT: return new HFieldF();
			case Type.DOUBLE: return new HFieldH();
			case Type.LONG: return new HFieldJ();
		}
	}
}
