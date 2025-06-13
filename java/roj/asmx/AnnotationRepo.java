package roj.asmx;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asmx.AnnotatedElement.Node;
import roj.asmx.AnnotatedElement.Type;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
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
 * @since 2023/12/26 12:47
 */
public final class AnnotationRepo {
	private final HashMap<String, Set<AnnotatedElement>> annotations = new HashMap<>();

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

	public void addOptionalCache(ZipFile zf) throws IOException {
		try {
			ZEntry entry = zf.getEntry("META-INF/annotations.repo");
			if (entry != null && entry.getSize() <= 1048576) {
				if (deserialize(IOUtil.getSharedByteBuf().readStreamFully(zf.getStream(entry)))) return;
			}
		} catch (Exception ignored) {}

		add(zf);
	}

	public void add(ClassNode data) {
		Type klass = new Type(data);
		add2(data.cp, data, klass);
		add1(data, data.fields, klass);
		add1(data, data.methods, klass);
	}
	private void add1(ClassNode data, List<? extends MemberNode> nodes, Type klass) {
		for (int i = nodes.size()-1; i >= 0; i--) {
			MemberNode node = nodes.get(i);
			Node subNode = new Node(klass, node);
			add2(data.cp, node, subNode);

			if (!subNode.annotations.isEmpty()) {
				if (klass.children.isEmpty())
					klass.children = new HashSet<>();
				klass.children.add(subNode);
			}
		}
	}
	private void add2(ConstantPool cp, Attributed node, AnnotatedElement info) {
		Annotations attr = node.getAttribute(cp, Attribute.RtAnnotations);
		if (attr != null) for (int i = 0; i < attr.annotations.size(); i++) {
			add3(info, attr.annotations.get(i));
		}
		attr = node.getAttribute(cp, Attribute.ClAnnotations);
		if (attr != null) for (int i = 0; i < attr.annotations.size(); i++) {
			add3(info, attr.annotations.get(i));
		}
	}
	private void add3(AnnotatedElement info, Annotation anno) {
		info.annotations.put(anno.type(), anno);
		annotations.computeIfAbsent(anno.type(), Helpers.fnHashSet()).add(info);
	}

	private final ArrayList<Object> rawNodes = new ArrayList<>();
	public void addRaw(DynByteBuf r, String fileName) {
		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		r.rIndex += 4;

		var cp = AsmCache.getInstance().constPool();
		cp.read(r, ConstantPool.BYTE_STRING);

		var acc = r.readChar();
		var name = intern(cp.getRefName(r, Constant.CLASS));
		if (name.endsWith("-info") || !name.concat(".class").equals(fileName)) return;
		var parent = cp.getRefName(r);
		if (parent != null) parent = intern(parent);

		var skeleton = new ClassView(null, -1, name, parent);
		skeleton.modifier = acc;

		Type klass = new Type(skeleton);

		rawNodes.clear();
		int len = r.readUnsignedShort();
		for (int i = 0; i < len; i++) rawNodes.add(intern(cp.getRefName(r, Constant.CLASS)));
		skeleton.interfaces = Helpers.cast(ArrayUtil.immutableCopyOf(rawNodes));

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
								klass.children = new HashSet<>();
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

			if (i == 0) skeleton.fields = Helpers.cast(ArrayUtil.immutableCopyOf(rawNodes));
			else skeleton.methods = Helpers.cast(ArrayUtil.immutableCopyOf(rawNodes));
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
		AsmCache.getInstance().constPool(cp);
	}

	public Set<AnnotatedElement> annotatedBy(String type) { return annotations.getOrDefault(type, Collections.emptySet()); }
	public HashMap<String, Set<AnnotatedElement>> getAnnotations() {return annotations;}

	public void serialize(DynByteBuf buf) {
		ToIntMap<Annotation> annotations = new ToIntMap<>();
		ToIntMap<Object> elements = new ToIntMap<>();

		annotations.put(null, 0);
		elements.put(null, 0);

		var cp = AsmCache.getInstance().constPool();
		int start = buf.wIndex();

		for (var value : this.annotations.values()) {
			for (var el : value) {
				Type parent = el.parent();
				int size = elements.size();
				int idx = elements.putOrGet(parent, size, 0);
				if (idx == 0) {
					buf.put(0);
					writeAcc(buf, parent, cp, elements);
					idx = parent == el ? size : elements.getInt(el.node());
				} else if (parent != el) {
					idx = elements.getInt(el.node());
				}
				buf.putVUInt(idx).putVUInt(el.annotations.size());
				for (var annotation : el.annotations.values()) {
					idx = annotations.putOrGet(annotation, annotations.size(), 0);
					if (idx == 0) {
						buf.put(0);
						annotation.toByteArray(buf, cp);
					} else {
						buf.putVUInt(idx);
					}
				}
			}
		}

		int cpl = 8 + 8 + cp.byteLength();
		buf.preInsert(start, cpl);
		int widx = buf.wIndex();
		buf.wIndex(start);
		buf.putAscii("ANNOREP").put(0).putShort(annotations.size()).putShort(elements.size()).putShort(this.annotations.size());
		cp.write(buf, false);
		buf.wIndex(widx);
		AsmCache.getInstance().constPool(cp);
	}
	private static void writeAcc(DynByteBuf buf, Type parent, ConstantPool cp, ToIntMap<Object> elements) {
		var owner = parent.owner;
		buf.putShort(cp.getUtfId(owner.name()))
		   .putShort(cp.getUtfId(owner.parent()))
		   .putShort(owner.modifier())
		   .putShort(owner.interfaces().size());
		for (String itf : owner.interfaces()) buf.putShort(cp.getUtfId(itf));
		buf.putShort(owner.methods().size());
		for (var node : owner.methods()) {
			elements.putIfAbsent(node, elements.size());
			buf.putShort(cp.getUtfId(node.name())).putShort(cp.getUtfId(node.rawDesc())).putShort(node.modifier());
		}
		buf.putShort(owner.fields().size());
		for (var node : owner.fields()) {
			elements.putIfAbsent(node, elements.size());
			buf.putShort(cp.getUtfId(node.name())).putShort(cp.getUtfId(node.rawDesc())).putShort(node.modifier());
		}
	}
	public boolean deserialize(DynByteBuf buf) {
		if (!buf.readAscii(7).equals("ANNOREP") || buf.readUnsignedByte() != 0)
			return false;

		var annotations = new Annotation[buf.readUnsignedShort()];
		int annotationCount = 0;
		var elements = new AnnotatedElement[buf.readUnsignedShort()];
		int elementCount = 0;

		int repoSize = buf.readUnsignedShort();
		this.annotations.ensureCapacity(repoSize);

		var cp = AsmCache.getInstance().constPool();
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
				this.annotations.computeIfAbsent(annotation.type(), Helpers.fnHashSet()).add(ae);
			}
		}

		AsmCache.getInstance().constPool(cp);
		return true;
	}
	private int readAcc(DynByteBuf r, ConstantPool cp, AnnotatedElement[] elements, int elementCount) {
		var skeleton = new ClassView(null, 0, ((CstUTF) cp.get(r)).str(), ((CstUTF) cp.get(r)).str());
		skeleton.modifier = r.readChar();

		rawNodes.clear();
		int len = r.readUnsignedShort();
		for (int i = 0; i < len; i++) rawNodes.add(((CstUTF) cp.get(r)).str());
		skeleton.interfaces = Helpers.cast(ArrayUtil.immutableCopyOf(rawNodes));

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

			if (i == 0) skeleton.fields = Helpers.cast(ArrayUtil.immutableCopyOf(rawNodes));
			else skeleton.methods = Helpers.cast(ArrayUtil.immutableCopyOf(rawNodes));
		}

		return elementCount;
	}
}