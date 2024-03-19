package roj.asmx;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
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
import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
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
				ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(za.getStream(ze)));
				if (data.name.concat(".class").equalsIgnoreCase(ze.getName())) {
					add(data);
				}
			}
		}
	}
	public void add(ConstantData data) {
		AccessData ad = data.toAccessData();
		Type klass = new Type(ad);
		addAnnotations(data.cp, data, klass);
		addChildren(data, data.fields, ad.fields, klass);
		addChildren(data, data.methods, ad.methods, klass);
	}

	private void addChildren(ConstantData data, List<? extends CNode> nodes, List<AccessData.MOF> anodes, Type klass) {
		for (int i = nodes.size()-1; i >= 0; i--) {
			CNode node = nodes.get(i);
			Node subNode = new Node(klass, anodes.get(i));
			addAnnotations(data.cp, node, subNode);
			if (subNode.annotations.isEmpty()) anodes.remove(i);
			else klass.children.add(subNode);
		}
	}
	private void addAnnotations(ConstantPool cp, Attributed node, AnnotatedElement info) {
		Annotations attr = node.parsedAttr(cp, Attribute.RtAnnotations);
		if (attr != null) addAnnotations(attr.annotations, info);
		attr = node.parsedAttr(cp, Attribute.ClAnnotations);
		if (attr != null) addAnnotations(attr.annotations, info);
	}
	private void addAnnotations(List<Annotation> list, AnnotatedElement info) {
		for (int i = 0; i < list.size(); i++) {
			Annotation anno = list.get(i);

			info.annotations.put(anno.type(), anno);
			annotations.computeIfAbsent(anno.type(), Helpers.cast(Helpers.myhashsetfn)).add(info);
		}
	}

	public Set<AnnotatedElement> annotatedBy(String type) { return annotations.getOrDefault(type, Collections.emptySet()); }
	public MyHashMap<String, Set<AnnotatedElement>> getAnnotations() {return annotations;}
}