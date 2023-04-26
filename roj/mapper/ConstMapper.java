package roj.mapper;

import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMAInputStream;
import roj.archive.qz.xz.LZMAOutputStream;
import roj.archive.zip.ZipUtil;
import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstDynamic;
import roj.asm.cst.CstRef;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.AttrRecord;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.type.Signature;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.collect.*;
import roj.concurrent.collect.ConcurrentFindHashMap;
import roj.concurrent.collect.ConcurrentFindHashSet;
import roj.io.IOUtil;
import roj.mapper.util.Desc;
import roj.mapper.util.MapperList;
import roj.mapper.util.SubImpl;
import roj.text.StringPool;
import roj.ui.CmdUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.NotDirectoryException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 第二代Class映射器
 *
 * @author Roj234
 * @version 2.9
 * @since 2020/8/19 22:13
 */
public class ConstMapper extends Mapping {
	public static final String DONT_LOAD_PREFIX = "[x]";
	public static final int FLAG_STATIC_MAP = 1, FLAG_CHECK_SUB_IMPL = 2, TRIM_DUPLICATE = 4, FLAG_CHECK_ANNOTATION = 8;

	// 'CMPC': Const Remapper Cache
	private static final int FILE_HEADER = 0x634d5063;

	private static final boolean DEBUG = false;

	/**
	 * 来自依赖的数据
	 */
	public final MyHashSet<Desc> libSkipped;
	public final MyHashMap<String, List<String>> libSupers;

	/**
	 * 工作中数据
	 */
	FindMap<Desc, String> selfMethods;
	FindSet<Desc> selfSkipped;
	Map<String, List<String>> selfSupers;

	public byte flag = FLAG_STATIC_MAP;
	private final List<State> extendedSuperList = new SimpleList<>();

	public ConstMapper() {
		this(false);
	}

	public ConstMapper(boolean checkFieldType) {
		super(checkFieldType);
		libSupers = new MyHashMap<>(128);
		libSkipped = new MyHashSet<>();
	}

	public ConstMapper(ConstMapper o) {
		super(o);
		this.libSupers = o.libSupers;
		this.libSkipped = o.libSkipped;
	}

	// region 缓存

	private static LZMA2Options getLZMAOption() {
		return new LZMA2Options(6).setDictSize(2097152).setPb(0);
	}

	public void saveCache(long hash, File cache) throws IOException {
		StringPool pool = new StringPool();
		if (!checkFieldType) pool.add("");

		ByteList w = IOUtil.getSharedByteBuf();

		w.putVUInt(classMap.size());
		for (Map.Entry<String, String> s : classMap.entrySet()) {
			pool.writeString(pool.writeString(w, s.getKey()), s.getValue());
		}
		w.putInt(FILE_HEADER);

		w.putVUInt(fieldMap.size());
		for (Map.Entry<Desc, String> s : fieldMap.entrySet()) {
			Desc descriptor = s.getKey();
			pool.writeString(pool.writeString(pool.writeString(pool.writeString(w, descriptor.owner), descriptor.name), descriptor.param).putShort(descriptor.flags), s.getValue());
		}
		w.putInt(FILE_HEADER);

		w.putVUInt(methodMap.size());
		for (Map.Entry<Desc, String> s : methodMap.entrySet()) {
			Desc descriptor = s.getKey();
			pool.writeString(pool.writeString(pool.writeString(pool.writeString(w, descriptor.owner), descriptor.name), descriptor.param).putShort(descriptor.flags), s.getValue());
		}
		w.putInt(FILE_HEADER);

		w.putVUInt(libSupers.size());
		for (Map.Entry<String, List<String>> s : libSupers.entrySet()) {
			pool.writeString(w, s.getKey());

			List<String> list = s.getValue();
			w.putVUInt(list.size());
			for (int i = 0; i < list.size(); i++) pool.writeString(w, list.get(i));
		}
		w.putInt(FILE_HEADER);

		w.putVUInt(libSkipped.size());
		for (Desc s : libSkipped) {
			pool.writeString(pool.writeString(pool.writeString(w, s.owner), s.name), s.param);
		}

		try (OutputStream out = new LZMAOutputStream(new FileOutputStream(cache), getLZMAOption(), -1)) {
			ByteList.WriteOut w1 = new ByteList.WriteOut(out);
			pool.writePool(w1.putInt(FILE_HEADER).putLong(hash));
			w1.flush();

			w.writeToStream(out);
		}
	}

	public Boolean readCache(long hash, File cache) throws IOException {
		if (cache.length() == 0) return null;
		ByteList r = IOUtil.getSharedByteBuf().readStreamFully(new LZMAInputStream(new FileInputStream(cache)));

		if (r.readInt() != FILE_HEADER) throw new IllegalArgumentException("file header");

		boolean readClassInheritanceMap = r.readLong() == hash;

		StringPool pool = new StringPool(r);

		int len = r.readVUInt();
		for (int i = 0; i < len; i++) {
			classMap.put(pool.readString(r), pool.readString(r));
		}
		if (r.readInt() != FILE_HEADER) throw new IllegalArgumentException("class map");

		len = r.readVUInt();
		fieldMap.ensureCapacity(len);
		for (int i = 0; i < len; i++) {
			fieldMap.put(new Desc(pool.readString(r), pool.readString(r), pool.readString(r), r.readUnsignedShort()), pool.readString(r));
		}
		if (r.readInt() != FILE_HEADER) throw new IllegalArgumentException("field map");

		len = r.readVUInt();
		methodMap.ensureCapacity(len);
		for (int i = 0; i < len; i++) {
			methodMap.put(new Desc(pool.readString(r), pool.readString(r), pool.readString(r), r.readUnsignedShort()), pool.readString(r));
		}
		if (r.readInt() != FILE_HEADER) throw new IllegalArgumentException("method map");

		if (!readClassInheritanceMap) return false;

		len = r.readVUInt();
		for (int i = 0; i < len; i++) {
			String name = pool.readString(r);

			int len2 = r.readVUInt();
			MapperList list2 = new MapperList(len2);
			for (int j = 0; j < len2; j++) {
				list2.add(pool.readString(r));
			}
			list2._init_();

			libSupers.put(name, list2);
		}
		if (r.readInt() != FILE_HEADER) throw new IllegalArgumentException("library super map");

		len = r.readVUInt();
		for (int i = 0; i < len; i++) {
			libSkipped.add(new Desc(pool.readString(r), pool.readString(r), pool.readString(r)));
		}

		return true;
	}

	// endregion
	// region 映射

	/**
	 * 全量
	 */
	public void remap(boolean singleThread, List<Context> arr) {
		if (singleThread || arr.size() <= 1000) {
			initSelf(arr.size());

			Context curr = null;
			try {
				for (int i = 0; i < arr.size(); i++) {
					S1_parse(curr = arr.get(i));
				}

				initSelfSuperMap();
				if ((flag & FLAG_CHECK_SUB_IMPL) != 0) S15_ignoreSubImpl(arr);

				for (int i = 0; i < arr.size(); i++) {
					S2_mapSelf(curr = arr.get(i));
				}

				for (int i = 0; i < arr.size(); i++) {
					S3_mapConstant(curr = arr.get(i));
				}

			} catch (Throwable e) {
				throw new RuntimeException("At parsing " + curr, e);
			}
		} else {
			selfSkipped = new ConcurrentFindHashSet<>(libSkipped);
			selfSupers = new ConcurrentHashMap<>(arr.size());
			selfMethods = new ConcurrentFindHashMap<>();

			List<List<Context>> splatted = new ArrayList<>(arr.size() / 1000 + 1);

			int i = 0;
			while (i < arr.size()) {
				int cnt = Math.min(arr.size() - i, 1000);
				splatted.add(arr.subList(i, i + cnt));
				i += cnt;
			}

			MapUtil.async(this::S1_parse, splatted);

			initSelfSuperMap();
			if ((flag & FLAG_CHECK_SUB_IMPL) != 0) S15_ignoreSubImpl(arr);

			MapUtil.async(this::S2_mapSelf, splatted);
			MapUtil.async(this::S3_mapConstant, splatted);
		}
	}

	/**
	 * 增量
	 */
	public void remapIncrement(List<Context> arr) {
		if (selfSupers == null) {
			initSelf(arr.size());
		}

		Context curr = null;

		try {
			MyHashSet<String> modified = new MyHashSet<>();
			for (int i = 0; i < arr.size(); i++) {
				S1_parse(curr = arr.get(i));
				modified.add(curr.getData().name);
			}

			Predicate<Desc> rem = key -> modified.contains(key.owner);
			selfMethods.keySet().removeIf(rem);
			selfSkipped.removeIf(rem);
			if ((flag & FLAG_CHECK_SUB_IMPL) != 0) S15_ignoreSubImpl(arr);

			initSelfSuperMap();

			for (int i = 0; i < arr.size(); i++) {
				S2_mapSelf(curr = arr.get(i));
			}

			for (int i = 0; i < arr.size(); i++) {
				S3_mapConstant(curr = arr.get(i));
			}

		} catch (Throwable e) {
			throw new RuntimeException("At parsing " + curr, e);
		}
	}

	// endregion

	/**
	 * Step 1 Prepare Super Mapping
	 */
	public final void S1_parse(Context c) {
		ConstantData data = c.getData();
		List<CstClass> itfs = data.interfaces;

		int cnt = itfs.size() + ("java/lang/Object".equals(data.parent) ? 0 : 1);
		if (cnt == 0) return;

		ArrayList<String> list = new ArrayList<>(cnt);
		if (!"java/lang/Object".equals(data.parent)) {
			list.add(data.parent);

			if ((flag & FLAG_CHECK_ANNOTATION) != 0) {
				Attribute attr = data.attrByName(Attribute.ClAnnotations.name);
				if (attr != null) {
					Annotations a = (Annotations) Parser.attr(data, data.cp, Attribute.ClAnnotations.name, Parser.reader(attr), Signature.CLASS);
					Annotation annotation = AttrHelper.getAnnotation(a.annotations, "roj/mapper/Inherited");
					if (annotation != null) {
						for (AnnVal klass : annotation.getArray("value")) {
							list.add(klass.asClass().owner);
						}
					}
				}
			}
		}

		for (int i = 0; i < itfs.size(); i++) {
			list.add(itfs.get(i).name().str());
		}

		selfSupers.put(data.name, list);
	}

	/**
	 * Step 1.5 (Optional) Find and Ignore SubImpl types
	 */
	public final List<Desc> S15_ignoreSubImpl(List<Context> ctxs) {
		List<Desc> filled = new ArrayList<>();
		MyHashMap<String, IClass> methods = new MyHashMap<>(ctxs.size());

		MyHashSet<SubImpl> subs = MapUtil.getInstance().gatherSubImplements(ctxs, this, methods);

		for (SubImpl impl : subs) {
			String targetName = null, foundClass = null;
			Desc desc = impl.type;
			for (String owner : impl.owners) {
				desc.owner = owner;
				String name1 = methodMap.get(desc);
				if (name1 != null) {
					if (targetName == null) {
						foundClass = owner;
						targetName = name1;
					} else if (!targetName.equals(name1)) {
						throw new IllegalStateException("SubImpl映射失败: 同参方法映射后名称不同");
					}
				}
			}
			if (targetName != null) {
				for (String owner : impl.owners) {
					if (owner.equals(foundClass)) continue;
					desc.owner = owner;
					if (!methods.containsKey(owner)) throw new IllegalArgumentException("SubImpl映射失败: 至多有一个Impl属于库/依赖\n" + "违反者: " + impl);
					if (null == methodMap.putIfAbsent(desc, targetName)) {
						filled.add(desc);
						List<? extends MoFNode> methods1 = methods.get(desc.owner).methods();
						for (int i = 0; i < methods1.size(); i++) {
							MoFNode m = methods1.get(i);
							if (desc.param.equals(m.rawDesc()) && desc.name.equals(m.name())) {
								desc.flags = m.modifier();
								break;
							}
						}
						if (desc.flags == Desc.UNSET) {
							throw new IllegalArgumentException("SubImpl映射失败: \n" + "无法适配权限\n" + "目标: " + desc);
						}
						desc = desc.copy();
					}
				}
			}
		}
		return filled;
	}

	/**
	 * Step 2 Map Inherited Methods
	 */
	public final void S2_mapSelf(Context ctx) {
		ConstantData data = ctx.getData();
		data.normalize();

		List<String> parents = selfSupers.getOrDefault(data.name, Collections.emptyList());

		Desc sp = MapUtil.getInstance().sharedDC;

		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			RawMethod m = (RawMethod) methods.get(i);

			sp.owner = data.name;
			sp.name = m.name.str();
			sp.param = m.type.str();

			int j = 0;
			while (true) {
				Map.Entry<Desc, String> entry = methodMap.find(sp);
				if (entry != null) {
					int flags = entry.getKey().flags;
					// 原方法无法被继承
					if (0 != (flags & (AccessFlag.STATIC | AccessFlag.PRIVATE | AccessFlag.FINAL))) {
						if (DEBUG) System.out.println("[2M-" + data.name + "-I]: " + sp.owner + '.' + sp.name + sp.param);
						// noinspection all
						if (sp.owner != data.name) {
							sp.owner = data.name;
							selfSkipped.add(sp.copy());
						} else {
							// 但是如果是自己...
							flags = Desc.UNSET;
						}
					} else if (0 != (flags & (AccessFlag.PUBLIC | AccessFlag.PROTECTED))) {
						flags = Desc.UNSET;
					} else { // 包相同可以继承
						if (!MapUtil.arePackagesSame(data.name, sp.owner)) {
							if (DEBUG) System.out.println("[2M-" + data.name + "-P]: " + sp.owner + '.' + sp.name + sp.param);
							// noinspection all
							if (sp.owner != data.name) {
								sp.owner = data.name;
								selfSkipped.add(sp.copy());
							}
						} else {
							flags = Desc.UNSET;
						}
					}

					if (flags == Desc.UNSET) {
						String newName = entry.getValue();
						m.name = data.cp.getUtf(newName);
						sp.owner = data.name;
						selfMethods.put(sp.copy(), newName);
					}
					break;
				}

				if (j == parents.size()) break;
				sp.owner = parents.get(j++);
			}
		}

		// 警告: field不能继承, 默认不改name
		List<? extends MoFNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			RawField f = (RawField) fields.get(i);

			sp.owner = data.name;
			sp.name = f.name.str();
			sp.param = checkFieldType ? f.type.str() : "";

			int j = 0;
			while (true) {
				if (fieldMap.containsKey(sp)) {
					if (DEBUG) System.out.println("[2F-" + data.name + "]: " + sp.owner + '.' + sp.name);
					// noinspection all
					if (sp.owner != data.name) {
						sp.owner = data.name;
						selfSkipped.add(sp.copy());
					}
					break;
				}

				if (j == parents.size()) break;
				sp.owner = parents.get(j++);
			}
		}
	}

	/**
	 * Step 2.5 (Optional) Field Name <br>
	 * Also implemented in CodeMapper
	 */
	public final void S25_mapFieldName(ConstantData data) {
		Desc md = MapUtil.getInstance().sharedDC;
		md.owner = data.name;
		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			RawField field = (RawField) fields.get(i);

			md.name = field.name.str();
			md.param = checkFieldType ? field.type.str() : "";

			String newName = fieldMap.get(md);
			if (newName != null) {
				if (DEBUG) {
					System.out.println("[F25-" + data.name + "]: " + field.name + " => " + newName);
				}
				field.name = data.cp.getUtf(newName);
			}
		}

		mapRecord(md, data);
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
				if (DEBUG) System.out.println("[25R-" + data.name + "]: " + sp.owner + '.' + sp.name + " => " + newName);
				ibm.name = newName;
			}
		}
	}

	// region Step 3: Map Class Use Other

	/**
	 * Step 3
	 */
	public final void S3_mapConstant(Context ctx) {
		ConstantData data = ctx.getData();

		BootstrapMethods bs = null;

		List<Constant> list = data.cp.array();
		for (int j = 0; j < list.size(); j++) {
			Constant c = list.get(j);
			switch (c.type()) {
				case Constant.INTERFACE:
				case Constant.METHOD:
					mapRef(data, (CstRef) c, true);
					break;
				case Constant.FIELD:
					mapRef(data, (CstRef) c, false);
					break;
				case Constant.INVOKE_DYNAMIC:
					if (bs == null) bs = data.parsedAttr(data.cp,Attribute.BootstrapMethods);
					if (bs == null) throw new IllegalArgumentException("有lambda却无BootstrapMethod, " + data.name);
					mapLambda(bs, data, (CstDynamic) c);
					break;
			}
		}
	}

	/**
	 * Map: lambda method name
	 */
	private void mapLambda(BootstrapMethods bs, ConstantData data, CstDynamic dyn) {
		if (dyn.tableIdx >= bs.methods.size()) {
			throw new IllegalArgumentException("BootstrapMethod id 不存在: " + (int) dyn.tableIdx + " at class " + data.name);
		}
		BootstrapMethods.BootstrapMethod ibm = bs.methods.get(dyn.tableIdx);
		if (!ibm.isInvokeMethod()) return;

		Desc md = MapUtil.getInstance().sharedDC;

		md.name = dyn.desc().name().str();
		// FP: init / clinit
		if (md.name.startsWith("<")) return;

		String allDesc = dyn.desc().getType().str();
		if (!allDesc.endsWith(";")) return;

		md.param = ibm.interfaceDesc();
		md.owner = TypeHelper.parseReturn(allDesc).owner;

		List<String> parents = selfSupers.getOrDefault(md.owner, Collections.emptyList());
		int i = 0;
		while (true) {
			String name = methodMap.get(md);
			if (name != null) {
				dyn.setDesc(data.cp.getDesc(name, allDesc));
				return;
			}

			if (selfSkipped.contains(md)) {
				if (DEBUG) System.out.println("[3L-" + data.name + "]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param);
				break;
			}

			if (i == parents.size()) break;
			md.owner = parents.get(i++);
		}
	}

	/**
	 * Map: use other(non-self) methods/fields
	 */
	private void mapRef(ConstantData data, CstRef ref, boolean method) {
		Desc md = MapUtil.getInstance().sharedDC.read(ref);

		if (method) {
			// FP: init / clinit
			if (ref.desc().name().str().startsWith("<")) return;
			/**
			 * Fast path
			 */
			String fpName = selfMethods.get(md);

			if (fpName != null) {
				setRefName(data, ref, fpName);
				return;
			}
		} else {
			if (!checkFieldType) {
				md.param = "";
			}
		}

		FindMap<Desc, String> map = method ? methodMap : fieldMap;
		List<String> parents = selfSupers.getOrDefault(md.owner, Collections.emptyList());
		int i = 0;
		while (true) {
			String name = map.get(md);
			if (name != null) {
				setRefName(data, ref, name);
				break;
			}

			if (selfSkipped.contains(md)) {
				if (DEBUG) System.out.println("[3" + (method ? "M" : "F") + "-" + data.name + "]: " + (!md.owner.equals(data.name) ? md.owner + '.' : "") + md.name + md.param);
				break;
			}

			if (i == parents.size()) break;
			md.owner = parents.get(i++);
		}
	}

	// endregion
	// region 读取libraries

	public final void loadLibraries(File folder) {
		if (!folder.isDirectory()) {
			Helpers.athrow(new NotDirectoryException(folder.getAbsolutePath()));
		}

		loadLibraries(IOUtil.findAllFiles(folder));
	}

	public void loadLibraries(List<?> files) {
		FileReader cb = new FileReader();

		for (int i = 0; i < files.size(); i++) {
			Object o = files.get(i);
			if (o instanceof File) {
				File fi = (File) o;
				String f = fi.getName().toLowerCase(Locale.ROOT);
				if (!f.startsWith(DONT_LOAD_PREFIX) && (f.endsWith(".zip") || f.endsWith(".jar"))) ZipUtil.unzip(fi, cb, (ze) -> ze.getName().endsWith(".class"));
			} else {
				List<Context> ctx = Helpers.cast(o);
				for (int j = 0; j < ctx.size(); j++) {
					cb.read(ctx.get(j).getData());
				}
			}
		}

		makeInheritMap(libSupers, (flag & FLAG_STATIC_MAP) != 0 ? classMap : null);

		// 下面这段的目的：用户类可能继承了映射中的方法
		// 并对这些方法做了修改，使得继承这些用户类的方法不能再映射
		// 1. 被标记了final
		// 2. static/private的方法，又出现了同名同参的方法
		//    Contract: static 无法继承为 non-static
		// 3. package-private 一旦被覆盖，就无法再继承 (只能cast再调用)
		//    A ~ /: package a
		//    B ~ A: public a
		//    C ~ B: 无法覆盖A的方法a
		//
		// 以及字段的"继承"
		//  垃圾java编译器对于static字段 A.x -> B
		//  会使用 getstatic B.x
		//  所以字段的访问需要筛选
		// 1. 子类拥有同名同类型字段: 筛选掉
		MyBitSet visited = new MyBitSet();
		Desc m = new Desc("", "", "");
		for (IClass data : cb.classes) {
			List<String> parents = libSupers.get(data.name());
			if (parents == null) continue;
			//boolean d = data.className().endsWith("x");

			List<? extends MoFNode> methods = data.methods();
			List<? extends MoFNode> fields = data.fields();
			for (int i = 0; i < parents.size(); i++) {
				if (!classMap.containsKey(parents.get(i))) continue;

				m.owner = parents.get(i);
				for (int j = 0; j < methods.size(); j++) {
					if (visited.contains(j)) continue;
					MoFNode method = methods.get(j);
					m.name = method.name();
					m.param = method.rawDesc();

					Map.Entry<Desc, String> entry = methodMap.find(m);
					test:
					if (entry != null) {
						//if (d) System.out.println("found " + m + " => " + entry.getValue());
						Desc md = entry.getKey();
						if (md.flags == Desc.UNSET) throw new IllegalStateException("缺少元素 " + md);
						if ((md.flags & (AccessFlag.FINAL | AccessFlag.STATIC)) != 0 || 0 != (method.modifier() & (AccessFlag.PRIVATE | AccessFlag.STATIC))) {

							//if (d) System.out.println("fall 1: " + method.accessFlag() + "\n" + md.flags);
							// 第一类第二类
							m.owner = data.name();
							libSkipped.add(m);
							visited.add(j);

							m = new Desc(parents.get(i), "");
							break test;
						} else if (0 == (md.flags & (AccessFlag.PROTECTED | AccessFlag.PUBLIC))) {
							//if (d) System.out.println("fall 2");
							// 第三类
							if (!MapUtil.arePackagesSame(data.name(), m.owner)) {
								//if (d) System.out.println("fall 3");
								m.owner = data.name();
								libSkipped.add(m);
								visited.add(j);

								m = new Desc(parents.get(i), "");
								break test;
							}
						}
						if ((flag & TRIM_DUPLICATE) != 0) {
							//if (d) System.out.println("fall 4");
							m.owner = data.name();
							methodMap.remove(m);
							m.owner = parents.get(i);
						}
					}
				}
				m.param = "";
				for (int j = 0; j < fields.size(); j++) {
					if (visited.contains(j + methods.size())) continue;
					MoFNode field = fields.get(j);
					m.name = field.name();
					if (checkFieldType) {
						m.param = field.rawDesc();
					}

					if (fieldMap.containsKey(m)) {
						m.owner = data.name();
						if (DEBUG) System.out.println("Dbg: skip inherit " + m);
						libSkipped.add(m);
						m = new Desc(parents.get(i), "");

						visited.add(j + methods.size());
					}
				}
			}
			visited.clear();
		}

		handleUnmatched(cb.flags);
	}

	protected void handleUnmatched(MyHashSet<Desc> rest) {
		if (!rest.isEmpty()) {
			System.out.println("[ConstMapper/WARN]: 缺少元素("+rest.size()+"): " + (DEBUG?rest:rest.iterator().next()));

			for (Desc missing : rest) { // 将没有flag的全部填充为public
				missing.flags = AccessFlag.PUBLIC;
			}
		}
	}

	@SuppressWarnings("unchecked")
	public final void initEnv(@Nullable Object map, @Nullable Object libPath, @Nullable File cacheFile, boolean reverse) throws IOException {
		clear();

		long hash = FILE_HEADER;
		if (cacheFile != null && libPath != null) {
			List<?> list;
			if (libPath instanceof File) {
				File folder = (File) libPath;
				if (!folder.isDirectory()) {
					throw new IllegalArgumentException(new FileNotFoundException(folder.getAbsolutePath()));
				}

				list = IOUtil.findAllFiles(folder);
			} else {list = (List<Object>) libPath;}
			hash = MapUtil.libHash(list);
		}

		if (cacheFile == null || !cacheFile.exists()) {
			if (map instanceof File) loadMap((File) map, reverse);
			else if (map instanceof InputStream) loadMap((InputStream) map, reverse);
			else if (map instanceof ByteList) loadMap(((ByteList) map).asInputStream(), reverse);

			if (libPath != null) {
				if (libPath instanceof File) loadLibraries((File) libPath);
				else loadLibraries((List<Object>) libPath);
			}
			if (cacheFile != null) saveCache(hash, cacheFile);
		} else {
			Boolean result = null;
			try {
				result = readCache(hash, cacheFile);
			} catch (Throwable e) {
				if (!(e instanceof FileNotFoundException)) {
					if (!(e instanceof IllegalArgumentException)) {
						CmdUtil.warning("缓存读取失败!", e);
					} else {
						CmdUtil.warning("缓存读取失败: " + e.getMessage());
					}
				}
			} finally {
				if (result == null) {
					clear();

					if (map instanceof File) loadMap((File) map, reverse);
					else if (map instanceof InputStream) loadMap((InputStream) map, reverse);
					else if (map instanceof ByteList) loadMap(((ByteList) map).asInputStream(), reverse);
				}
				if (libPath != null && result != Boolean.TRUE) {
					if (libPath instanceof File) loadLibraries((File) libPath);
					else loadLibraries((List<Object>) libPath);
				}
				saveCache(hash, cacheFile);
			}
		}
	}

	// endregion
	// region 工具方法

	/**
	 * Set reference name
	 */
	static void setRefName(ConstantData data, CstRef ref, String newName) {
		ref.desc(data.cp.getDesc(newName, ref.desc().getType().str()));
	}

	public void clear() {
		classMap.clear();
		fieldMap.clear();
		methodMap.clear();
		libSupers.clear();
		libSkipped.clear();
	}

	public final void initSelfSuperMap() {
		Map<String, List<String>> universe = new MyHashMap<>(this.libSupers);
		for (int i = 0; i < extendedSuperList.size(); i++) {
			State state = extendedSuperList.get(i);
			if (state != null) universe.putAll(state.map);
		}
		universe.putAll(this.selfSupers); // replace lib class
		this.selfSupers = universe;

		makeInheritMap(universe, (flag & (FLAG_STATIC_MAP | FLAG_CHECK_SUB_IMPL)) == FLAG_STATIC_MAP ? classMap : null);
	}

	public final void initSelf(int size) {
		selfSkipped = new MyHashSet<>(libSkipped);
		selfSupers = new MyHashMap<>(size);
		selfMethods = new MyHashMap<>();
	}

	public final State snapshot() {
		return snapshot(null);
	}

	public final State snapshot(State state) {
		if (state == null) {
			state = new State();
		}

		if (selfSupers == null) throw new IllegalStateException();
		state.map.clear();
		state.map.putAll(selfSupers);
		return state;
	}

	public final void state(State state) {
		if (selfSupers != null) {
			selfSupers.clear();
			selfSupers.putAll(state.map);
		} else {
			selfSupers = new MyHashMap<>(state.map);
		}
	}

	public FindSet<Desc> getSelfSkipped() {
		return selfSkipped;
	}

	public FindMap<Desc, String> getSelfMethods() {
		return selfMethods;
	}

	public Map<String, List<String>> getSelfSupers() {
		return selfSupers;
	}

	public final List<State> getExtendedSuperList() {
		return extendedSuperList;
	}

	public static final class State {
		final MyHashMap<String, List<String>> map = new MyHashMap<>();
	}

	private final class FileReader implements ZipUtil.ICallback {
		final MyHashSet<IClass> classes = new MyHashSet<>();
		final MyHashSet<Desc> flags;
		private final Desc desc = new Desc();

		public FileReader() {
			// 更新FlagList的访问权限
			flags = new MyHashSet<>(methodMap.size()+fieldMap.size());
			flags.addAll(methodMap.keySet());
			flags.addAll(fieldMap.keySet());
		}

		@Override
		public void onRead(String fileName, InputStream s) throws IOException {
			if (s.available() < 32) return;

			AccessData data;
			try {
				data = Parser.parseAcc0(null, IOUtil.getSharedByteBuf().readStreamFully(s));
			} catch (Throwable e) {
				CmdUtil.warning(fileName + " 无法读取", e);
				return;
			}
			read(data);
		}

		void read(IClass data) {
			if (!libSupers.containsKey(data.name())) {
				SimpleList<String> list = new SimpleList<>(data.interfaces().size()+1);
				if (!"java/lang/Object".equals(data.parent())) {
					list.add(data.parent());
				}
				list.addAll(data.interfaces());

				// 构建lib一极继承表
				if (!list.isEmpty()) {
					libSupers.put(data.name(), list);
					if ((flag & TRIM_DUPLICATE) != 0 || !classMap.containsKey(data.name())) classes.add(data);
				}
			}

			// 更新访问权限
			if (!classMap.containsKey(data.name())) return;

			Desc d = desc;
			d.owner = data.name();
			findAndReplace(d, data.methods(), true);
			d.param = "";
			findAndReplace(d, data.fields(), checkFieldType);
		}

		private void findAndReplace(Desc d, List<? extends MoFNode> nodes, boolean par) {
			for (int i = 0; i < nodes.size(); i++) {
				MoFNode n = nodes.get(i);
				d.name = n.name();
				if (par) d.param = n.rawDesc();

				Desc ent = flags.find(d);
				if (ent != d) {
					ent.flags = n.modifier();
					flags.remove(d);
				}
			}
		}
	}

	// endregion
}