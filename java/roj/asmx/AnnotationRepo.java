package roj.asmx;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.AsmShared;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.tree.AccessData;
import roj.asm.tree.Attributed;
import roj.asm.tree.CNode;
import roj.asm.tree.ConstantData;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asmx.AnnotatedElement.Node;
import roj.asmx.AnnotatedElement.Type;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static roj.text.Interner.intern;

/**
 * @author Roj234
 * @since 2023/12/26 0026 12:47
 */
public final class AnnotationRepo {
	private final MyHashMap<String, Set<AnnotatedElement>> annotations = new MyHashMap<>();

	public AnnotationRepo() {}

	public void add(File file) {
		try (ZipFile za = new ZipFile(file)) {
			add(za);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void add(ZipFile za) throws IOException {
		for (ZEntry ze : za.entries()) {
			if (IOUtil.extensionName(ze.getName()).equals("class")) {
				addRaw(IOUtil.getSharedByteBuf().readStreamFully(za.getStream(ze)), ze.getName());
			}
		}
	}

	public void add(ConstantData data) {
		Type klass = new Type(data);
		add2(data.cp, data, klass);
		add1(data, data.fields, klass);
		add1(data, data.methods, klass);
	}
	private void add1(ConstantData data, List<? extends CNode> nodes, Type klass) {
		for (int i = nodes.size()-1; i >= 0; i--) {
			CNode node = nodes.get(i);
			Node subNode = new Node(klass, node);
			add2(data.cp, node, subNode);

			if (!subNode.annotations.isEmpty()) {
				if (klass.children.isEmpty())
					klass.children = new MyHashSet<>();
				klass.children.add(subNode);
			}
		}
	}
	private void add2(ConstantPool cp, Attributed node, AnnotatedElement info) {
		Annotations attr = node.parsedAttr(cp, Attribute.RtAnnotations);
		if (attr != null) for (int i = 0; i < attr.annotations.size(); i++) {
			add3(info, attr.annotations.get(i));
		}
		attr = node.parsedAttr(cp, Attribute.ClAnnotations);
		if (attr != null) for (int i = 0; i < attr.annotations.size(); i++) {
			add3(info, attr.annotations.get(i));
		}
	}
	private void add3(AnnotatedElement info, Annotation anno) {
		info.annotations.put(anno.type(), anno);
		annotations.computeIfAbsent(anno.type(), Helpers.fnMyHashSet()).add(info);
	}

	private final SimpleList<Object> rawNodes = new SimpleList<>();
	public void addRaw(DynByteBuf r, String fileName) {
		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		r.rIndex += 4;

		var cp = AsmShared.local().constPool();
		cp.read(r, ConstantPool.BYTE_STRING);

		var acc = r.readChar();
		var name = intern(cp.getRefName(r));
		if (name.endsWith("-info") || !name.concat(".class").equals(fileName)) return;
		var parent = intern(cp.getRefName(r));

		var skeleton = new AccessData(null, -1, name, parent);
		skeleton.modifier = acc;

		Type klass = new Type(skeleton);

		rawNodes.clear();
		int len = r.readUnsignedShort();
		for (int i = 0; i < len; i++) rawNodes.add(intern(cp.getRefName(r)));
		skeleton.itf = Helpers.cast(ArrayUtil.copyOf(rawNodes));

		for (int i = 0; i < 2; i++) {
			len = r.readUnsignedShort();
			rawNodes.clear();
			while (len-- > 0) {
				acc = r.readChar();
				var node = skeleton.new MOF(intern(((CstUTF) cp.get(r)).str()), intern(((CstUTF) cp.get(r)).str()), 0);
				node.modifier = acc;

				int attrSize = r.readUnsignedShort();
				if (attrSize == 0) continue;

				AnnotatedElement.Node xnode = null;

				while (attrSize-- > 0) {
					var name1 = ((CstUTF) cp.get(r)).str();
					int length = r.readInt();
					if (name1.equals("RuntimeVisibleAnnotations") || name1.equals("RuntimeInvisibleAnnotations")) {
						if (xnode == null) {
							xnode = new Node(klass, node);
							if (klass.children.isEmpty())
								klass.children = new MyHashSet<>();
							klass.children.add(xnode);
							rawNodes.add(node);
						}

						int annoSize = r.readUnsignedShort();
						while (annoSize-- > 0) add3(xnode, Annotation.parse(cp, r));
					} else {
						r.rIndex += length;
					}
				}
			}

			if (i == 0) skeleton.fields = Helpers.cast(ArrayUtil.copyOf(rawNodes));
			else skeleton.methods = Helpers.cast(ArrayUtil.copyOf(rawNodes));
		}

		int attrSize = r.readUnsignedShort();
		while (attrSize-- > 0) {
			var name1 = ((CstUTF) cp.get(r)).str();
			int length = r.readInt();
			if (name1.equals("RuntimeVisibleAnnotations") || name1.equals("RuntimeInvisibleAnnotations")) {
				int annoSize = r.readUnsignedShort();
				while (annoSize-- > 0) add3(klass, Annotation.parse(cp, r));
			} else {
				r.rIndex += length;
			}
		}
		AsmShared.local().constPool(cp);
	}

	public Set<AnnotatedElement> annotatedBy(String type) { return annotations.getOrDefault(type, Collections.emptySet()); }
	public MyHashMap<String, Set<AnnotatedElement>> getAnnotations() {return annotations;}

	public void serialize(DynByteBuf buf) {
		ToIntMap<Annotation> annotations = new ToIntMap<>();
		ToIntMap<Object> elements = new ToIntMap<>();

		annotations.put(null, 0);
		elements.put(null, 0);

		var cp = AsmShared.local().constPool();
		var rest = IOUtil.getSharedByteBuf();

		for (var value : this.annotations.values()) {
			for (var el : value) {
				Type parent = el.parent();
				int size = elements.size();
				int idx = elements.putOrGet(parent, size, 0);
				if (idx == 0) {
					rest.put(0);
					writeAcc(rest, parent, cp, elements);
					idx = parent == el ? size : elements.getInt(el.node());
				}
				rest.putVUInt(idx).putVUInt(el.annotations.size());
				for (var annotation : el.annotations.values()) {
					idx = annotations.putOrGet(annotation, annotations.size(), 0);
					if (idx == 0) {
						rest.put(0);
						annotation.toByteArray(cp, rest);
					} else {
						rest.putVUInt(idx);
					}
				}
			}
		}

		buf.putAscii("ANNOREP").put(0).putShort(annotations.size()).putShort(elements.size()).putShort(this.annotations.size());
		cp.write(buf, false);
		buf.put(rest);
		AsmShared.local().constPool(cp);
	}
	private static void writeAcc(DynByteBuf buf, Type parent, ConstantPool cp, ToIntMap<Object> elements) {
		var owner = (AccessData) parent.owner;
		buf.putShort(cp.getUtfId(owner.name))
		   .putShort(cp.getUtfId(owner.parent))
		   .putShort(owner.modifier)
		   .putShort(owner.itf.size());
		for (String itf : owner.itf) buf.putShort(cp.getUtfId(itf));
		buf.putShort(owner.methods.size());
		for (var node : owner.methods) {
			elements.putIfAbsent(node, elements.size());
			buf.putShort(cp.getUtfId(node.name)).putShort(cp.getUtfId(node.desc)).putShort(node.modifier);
		}
		buf.putShort(owner.fields.size());
		for (var node : owner.fields) {
			elements.putIfAbsent(node, elements.size());
			buf.putShort(cp.getUtfId(node.name)).putShort(cp.getUtfId(node.desc)).putShort(node.modifier);
		}
	}
	public boolean deserialize(DynByteBuf buf) {
		if (!buf.readAscii(7).equals("ANNOREP") || buf.readUnsignedByte() != 0)
			return false;

		var annotations = new Annotation[buf.readShort()];
		int annotationCount = 0;
		var elements = new AnnotatedElement[buf.readShort()];
		int elementCount = 0;

		int repoSize = buf.readShort();
		this.annotations.ensureCapacity(repoSize);

		var cp = AsmShared.local().constPool();
		cp.read(buf, ConstantPool.CHAR_STRING);

		while (buf.isReadable()) {
			int id = buf.readVUInt();
			if (id == 0) {
				elementCount = readAcc(buf, cp, elements, elementCount);
				id = buf.readVUInt();
			}
			var ae = elements[id-1];

			int len = buf.readVUInt();
			while (len-- > 0) {
				id = buf.readVUInt();
				Annotation annotation;
				if (id == 0) {
					annotation = Annotation.parse(cp, buf);
					annotations[annotationCount++] = annotation;
				} else {
					annotation = annotations[id-1];
				}

				ae.annotations.put(annotation.type(), annotation);
				this.annotations.computeIfAbsent(annotation.type(), Helpers.fnMyHashSet()).add(ae);
			}
		}

		AsmShared.local().constPool(cp);
		return true;
	}
	private int readAcc(DynByteBuf r, ConstantPool cp, AnnotatedElement[] elements, int elementCount) {
		var skeleton = new AccessData(null, 0, ((CstUTF) cp.get(r)).str(), ((CstUTF) cp.get(r)).str());
		skeleton.modifier = r.readChar();

		rawNodes.clear();
		int len = r.readUnsignedShort();
		for (int i = 0; i < len; i++) rawNodes.add(((CstUTF) cp.get(r)).str());
		skeleton.itf = Helpers.cast(ArrayUtil.copyOf(rawNodes));

		Type type = new Type(skeleton);
		elements[elementCount++] = type;

		for (int i = 0; i < 2; i++) {
			len = r.readShort();

			rawNodes.clear();
			for (int j = 0; j < len; j++) {
				var mof = skeleton.new MOF(((CstUTF) cp.get(r)).str(), ((CstUTF) cp.get(r)).str(), 0);
				mof.modifier = r.readChar();
				rawNodes.add(mof);
				elements[elementCount++] = new Node(type, mof);
			}

			if (i == 0) skeleton.fields = Helpers.cast(ArrayUtil.copyOf(rawNodes));
			else skeleton.methods = Helpers.cast(ArrayUtil.copyOf(rawNodes));
		}

		return elementCount;
	}
}