package ilib.asm.FeOrg;

import org.objectweb.asm.Type;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstClass;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.asm.tree.Attributed;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.LoaderException;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.discovery.ModCandidate;
import net.minecraftforge.fml.common.discovery.asm.ASMModParser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Nixim(value = "/", copyItf = true)
public class NiximASMModParser extends ASMModParser implements FastParser, Consumer<Annotation> {
	@Shadow("/")
	private Type asmType;
	@Shadow("/")
	private int classVersion;
	@Shadow("/")
	private Type asmSuperType;

	NiximASMModParser() throws IOException {
		super(null);
	}

	void $$$CONSTRUCTOR() {}

	@Copy
	private ConstantData data;

	@Inject(value = "<init>", at = At.REPLACE)
	public void remapInit(InputStream in) {
		$$$CONSTRUCTOR();

		classAnnotations = Collections.emptyList();
		try {
			ByteList shared = IOUtil.getSharedByteBuf().readStreamFully(in);
			ConstantData data = this.data = Parser.parseConstants(shared);

			asmType = TypeHelper.asmType(data.name);
			asmSuperType = TypeHelper.asmType(data.parent);
			classVersion = data.version;
		} catch (Exception ex) {
			FMLLog.log.error("class加载失败", ex);
			throw new LoaderException(ex);
		}
	}

	@Inject("/")
	public String toString() {
		return "AnnotationDiscover[" + data.name + "]";
	}

	@Inject("/")
	public void sendToTable(ASMDataTable table, ModCandidate candidate) {
		List<CstClass> itf = data.interfaces;
		for (int i = 0; i < itf.size(); i++) {
			table.addASMData(candidate, itf.get(i).name().str(), data.name, null, null);
		}

		this.table = table;
		this.candidate = candidate;
		ConstantData data = this.data;
		this.QName = data.name.replace('/', '.');

		try {
			key = QName;
			getAnn(data.cp, data);

			List<? extends FieldNode> fields = data.fields;
			for (int i = 0; i < fields.size(); i++) {
				FieldNode fs = fields.get(i);
				key = fs.name();
				getAnn(data.cp, fs);
			}

			List<? extends MethodNode> methods = data.methods;
			for (int i = 0; i < methods.size(); i++) {
				MethodNode ms = methods.get(i);
				key = ms.name()+ms.rawDesc();
				getAnn(data.cp, ms);
			}
		} catch (Exception ex) {
			FMLLog.log.error("class加载失败", ex);
			throw new LoaderException(ex);
		} finally {
			this.table = null;
			this.candidate = null;
			this.data = null;
		}
	}

	@Copy
	private List<TypeHelper> classAnnotations;
	@Copy
	@Override
	public List<TypeHelper> getClassAnnotations() {
		return classAnnotations;
	}

	@Copy
	private void getAnn(ConstantPool cp, Attributed node) {
		Attribute attr0 = node.attrByName(Annotations.INVISIBLE);
		if (attr0 != null) {
			DynByteBuf r = Parser.reader(attr0);
			int i = r.readUnsignedShort();
			while (i-- > 0) accept(Annotation.deserialize(cp, r));
		}

		attr0 = node.attrByName(Annotations.VISIBLE);
		if (attr0 != null) {
			DynByteBuf r = Parser.reader(attr0);
			int i = r.readUnsignedShort();
			while (i-- > 0) accept(Annotation.deserialize(cp, r));
		}
	}

	@Copy
	private ASMDataTable table;
	@Copy
	private ModCandidate candidate;
	@Copy
	private String QName, key;
	@Override
	@Copy
	public void accept(Annotation ann) {
		Map<String, Object> val = TypeHelper.toPrimitive(ann.values);
		table.addASMData(candidate, ann.clazz.replace('/', '.'), QName, key, val);
		if (QName == key) {
			if (classAnnotations.isEmpty()) classAnnotations = new SimpleList<>(4);
			classAnnotations.add(new TypeHelper(TypeHelper.asmType(ann.clazz), val));
		}
	}
}