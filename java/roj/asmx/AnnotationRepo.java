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
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2023/12/26 0026 12:47
 */
public class AnnotationRepo {
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
			if (IOUtil.extensionName(ze.getName()).equalsIgnoreCase("class")) {
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

			if (!subNode.annotations.isEmpty())
				klass.children.add(subNode);
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
		annotations.computeIfAbsent(anno.type(), Helpers.cast(Helpers.myhashsetfn)).add(info);
	}

	private final SimpleList<AccessData.MOF> rawNodes = new SimpleList<>();
	public void addRaw(DynByteBuf r, String fileName) {
		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		r.rIndex += 4;

		ConstantPool cp = AsmShared.local().constPool();
		cp.read(r, ConstantPool.BYTE_STRING);

		var acc = r.readChar();
		var name = cp.getRefName(r);
		if (!name.concat(".class").equals(fileName)) return;
		var parent = cp.getRefName(r);

		var skeleton = new AccessData(null, -1, name, parent);
		skeleton.acc = acc;

		Type klass = new Type(skeleton);

		int len = r.readUnsignedShort();
		if (len > 0) {
			var itf = new String[len];
			for (int i = 0; i < len; i++) itf[i] = cp.getRefName(r);
			skeleton.itf = Arrays.asList(itf);
		}

		for (int i = 0; i < 2; i++) {
			len = r.readUnsignedShort();
			if (len == 0) continue;

			rawNodes.clear();
			while (len-- > 0) {
				acc = r.readChar();
				var node = skeleton.new MOF(((CstUTF) cp.get(r)).str(), ((CstUTF) cp.get(r)).str(), 0);
				node.acc = acc;

				int attrSize = r.readUnsignedShort();
				if (attrSize == 0) continue;

				AnnotatedElement.Node xnode = null;

				while (attrSize-- > 0) {
					var name1 = ((CstUTF) cp.get(r)).str();
					int length = r.readInt();
					if (name1.equals("RuntimeVisibleAnnotations") || name1.equals("RuntimeInvisibleAnnotations")) {
						if (xnode == null) {
							xnode = new Node(klass, node);
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

			if (!rawNodes.isEmpty()) {
				if (i == 0) skeleton.fields = ArrayUtil.copyOf(rawNodes);
				else skeleton.methods = ArrayUtil.copyOf(rawNodes);
			}
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
	}

	public Set<AnnotatedElement> annotatedBy(String type) { return annotations.getOrDefault(type, Collections.emptySet()); }
	public MyHashMap<String, Set<AnnotatedElement>> getAnnotations() {return annotations;}
}