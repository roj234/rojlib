package roj.asmx;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.tree.*;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Desc;
import roj.asm.util.ClassUtil;
import roj.asmx.AnnotationOwner.ClassInfo;
import roj.asmx.AnnotationOwner.NodeInfo;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Roj234
 * @since 2023/12/26 0026 12:47
 */
public class AnnotationRepo {
	private final MyHashMap<String, Set<AnnotationOwner>> annotations = new MyHashMap<>();
	private final DeclFilter filter;
	private Map<Object, Object> monitor;

	public AnnotationRepo(DeclFilter filter) { this.filter = filter; this.monitor = new MyHashMap<>(); }
	public AnnotationRepo() { filter = null; monitor = Collections.emptyMap(); }

	public void add(File file) {
		if (monitor == null) throw new IllegalStateException("finished");

		try (ZipArchive za = new ZipArchive(file)) {
			for (ZEntry ze : za.getEntries().values()) {
				if (IOUtil.extensionName(ze.getName()).equalsIgnoreCase("class")) {
					ConstantData data = Parser.parseConstants(IOUtil.getSharedByteBuf().readStreamFully(za.getInput(ze)));
					if (data.name.concat(".class").equalsIgnoreCase(ze.getName())) {
						add(data);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public void add(ConstantData data) {
		if (monitor == null) throw new IllegalStateException("finished");

		ClassInfo klass = new ClassInfo(data.name);
		addAnnotations(data.cp, data, klass, "");
		addChildren(data, data.fields, klass, "o");
		addChildren(data, data.methods, klass, "o");
	}
	public void finish() { monitor = null; }

	private void addChildren(ConstantData data, List<? extends CNode> nodes, ClassInfo klass, String marker) {
		for (int i = 0; i < nodes.size(); i++) {
			CNode node = nodes.get(i);
			NodeInfo subNode = new NodeInfo(data.name, node.name(), node.rawDesc());
			addAnnotations(data.cp, node, subNode, marker);
			klass.children.add(subNode);
		}
	}
	private void addAnnotations(ConstantPool cp, Attributed node, AnnotationOwner info, String marker) {
		Annotations attr = node.parsedAttr(cp, Attribute.RtAnnotations);
		if (attr != null) addAnnotations(attr.annotations, info, marker);
		attr = node.parsedAttr(cp, Attribute.ClAnnotations);
		if (attr != null) addAnnotations(attr.annotations, info, marker);
	}
	private void addAnnotations(List<Annotation> list, AnnotationOwner info, String marker) {
		Desc d = ClassUtil.getInstance().sharedDC; d.param = "";
		d.name = marker;

		for (int i = 0; i < list.size(); i++) {
			Annotation anno = list.get(i);

			info.annotations.put(anno.type, anno);
			annotations.computeIfAbsent(anno.type, Helpers.cast(Helpers.myhashsetfn)).add(info);

			d.owner = anno.type;
			Object val = monitor.get(d);
			if (val != null) {
				Object key = marker.isEmpty() ? info.owner() : new Desc(info.owner(), info.name(), info.desc());
				DeclFilter.add(filter.declare, key, val);
			}
		}
	}

	public Set<AnnotationOwner> annotatedBy(String type) { return annotations.getOrDefault(type, Collections.emptySet()); }

	public AnnotationRepo onClass(String annotation, NodeTransformer<? super ConstantData> tr) {
		if (filter == null) throw new UnsupportedOperationException("AnnotationFilter was not created with valid DeclFilter");
		for (AnnotationOwner info : annotatedBy(annotation)) {
			if (!info.isLeaf()) filter.declaredClass(info.owner(), tr);
		}
		if (monitor != null) DeclFilter.add(monitor, new Desc(annotation, ""), tr);
		return this;
	}
	public AnnotationRepo onField(String annotation, NodeTransformer<? super FieldNode> tr) {
		if (filter == null) throw new UnsupportedOperationException("AnnotationFilter was not created with valid DeclFilter");
		for (AnnotationOwner info : annotatedBy(annotation)) {
			if (info.isLeaf() && info.desc().lastIndexOf(')') < 0) {
				filter.declaredField(info.owner(), info.name(), info.desc(), tr);
			}
		}
		if (monitor != null) DeclFilter.add(monitor, new Desc(annotation, "o"), tr);
		return this;
	}
	public AnnotationRepo onMethod(String annotation, NodeTransformer<? super MethodNode> tr) {
		if (filter == null) throw new UnsupportedOperationException("AnnotationFilter was not created with valid DeclFilter");
		for (AnnotationOwner info : annotatedBy(annotation)) {
			if (info.isLeaf() && info.desc().lastIndexOf(')') > 0) {
				filter.declaredMethod(info.owner(), info.name(), info.desc(), tr);
			}
		}
		if (monitor != null) DeclFilter.add(monitor, new Desc(annotation, "o"), tr);
		return this;
	}
}