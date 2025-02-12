package roj.asmx.mapper;

import org.jetbrains.annotations.NotNull;
import roj.archive.qz.xz.LZMA2Options;
import roj.archive.qz.xz.LZMAInputStream;
import roj.archive.qz.xz.LZMAOutputStream;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.AsmShared;
import roj.asm.cp.*;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.*;
import roj.asm.type.Desc;
import roj.asm.type.Signature;
import roj.asm.type.TypeHelper;
import roj.asm.util.ClassUtil;
import roj.asm.util.Context;
import roj.asm.util.ReflectClass;
import roj.asmx.mapper.util.MapperList;
import roj.asmx.mapper.util.NameAndType;
import roj.asmx.mapper.util.SubImpl;
import roj.collect.*;
import roj.concurrent.TaskPool;
import roj.io.IOUtil;
import roj.io.MyDataInputStream;
import roj.text.CharList;
import roj.text.StringPool;
import roj.text.logging.Level;
import roj.ui.Terminal;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.ARRAY;

/**
 * @author Roj234
 * @version 3.1
 * @since 2020/8/19 22:13
 */
public class Mapper extends Mapping {
	public static final String DONT_LOAD_PREFIX = "[x]";
	public static final int
		/** 任意methodMap和fieldMap的owner均能在classMap中找到 */
		FLAG_FULL_CLASS_MAP = 1,
		/** (Obfuscator用) 删除冲突的mapping（方法继承） */
		FLAG_FIX_INHERIT = 4,
		/** (Obfuscator用) 删除冲突的mapping（子类实现的接口覆盖父类方法） */
		MF_FIX_SUBIMPL = 2,
		/** 启用注解伪继承 */
		MF_ANNOTATION_INHERIT = 8,
		/** 类名有变动 */
		MF_RENAME_CLASS = 16,
		/** 单线程 */
		MF_SINGLE_THREAD = 32,
		/** 修复class改名造成的访问问题 */
		MF_FIX_ACCESS = 64;

	// 'CMPC': Const Remapper Cache
	private static final int FILE_HEADER = 0x634d5063;
	private static final int ASYNC_THRESHOLD = 1000;
	private static final boolean DEBUG = false;
	// 注意事项
	// 1. 返回值的重载通过bridge method实现，JVM就是傻逼，当然也可以让你实现骚操作
	// 2. stopAnchor
	// 3. fixAccess inheritor

	private final UnaryOperator<String> GENERIC_TYPE_MAPPER = (old) -> {
		String now = ClassUtil.getInstance().mapClassName(classMap, old);
		return now == null ? old : now;
	};
	private final ParamNameMapper PARAM_TYPE_MAPPER = new ParamNameMapper() {
		@Override
		protected List<String> getNewParamName(MethodNode m) {
			if (paramMap != null) {
				String owner = m.ownerClass();

				Desc md = ClassUtil.getInstance().sharedDC;
				md.owner = classMap.getOrDefault(owner, owner);
				md.name = m.name();
				md.param = m.rawDesc();

				List<String> parents = selfSupers.getOrDefault(owner, Collections.emptyList());
				int i = 0;
				do {
					List<String> param = paramMap.get(md);
					if (param != null) return new SimpleList<>(param);

					if (i == parents.size()) break;
					md.owner = parents.get(i++);
				} while (true);
			}

			return new SimpleList<>();
		}

		@Override
		protected String mapType(String type) { return ClassUtil.getInstance().mapFieldType(classMap, type); }
		@Override
		protected String mapGeneric(String type) {
			Signature s = Signature.parse(type);
			s.rename(GENERIC_TYPE_MAPPER);
			return s.toDesc();
		}
	};

	private final List<State> extraStates = new SimpleList<>();
	/**
	 * 来自依赖的数据
	 */
	public final MyHashSet<Desc> libStopAnchor;
	public final MyHashMap<String, List<String>> libSupers;

	/**
	 * 工作中数据
	 */
	private Map<Desc, String> selfInherited;
	private Set<Desc> stopAnchor;
	Map<String, List<String>> selfSupers;

	public byte flag = FLAG_FULL_CLASS_MAP;

	public Mapper() { this(false); }
	public Mapper(boolean checkFieldType) {
		super(checkFieldType);
		libSupers = new MyHashMap<>(128);
		libStopAnchor = new MyHashSet<>();
	}

	public Mapper(Mapper o) {
		super(o);
		this.libStopAnchor = o.libStopAnchor;
		this.libSupers = o.libSupers;
		this.PARAM_TYPE_MAPPER.validNameChars = o.PARAM_TYPE_MAPPER.validNameChars;
	}
	public Mapper(Mapping o) {
		super(o);
		this.libSupers = new MyHashMap<>(128);
		this.libStopAnchor = new MyHashSet<>();
	}

	// region 缓存

	private static LZMA2Options getLZMAOption() {
		return new LZMA2Options(6).setDictSize(2097152).setPb(0);
	}

	public void saveCache(OutputStream cache, int saveMode) throws IOException {
		StringPool pool = new StringPool();
		if (!checkFieldType) pool.add("");

		ByteList w = IOUtil.getSharedByteBuf();

		if ((saveMode&1) != 0) {
			w.putVUInt(classMap.size());
			for (Map.Entry<String, String> s : classMap.entrySet()) {
				pool.add(pool.add(w, s.getKey()), s.getValue());
			}

			w.putVUInt(fieldMap.size());
			for (Map.Entry<Desc, String> s : fieldMap.entrySet()) {
				Desc desc = s.getKey();
				pool.add(pool.add(pool.add(pool.add(w, desc.owner), desc.name), desc.param).put(desc.flags), s.getValue());
			}

			w.putVUInt(methodMap.size());
			for (Map.Entry<Desc, String> s : methodMap.entrySet()) {
				Desc desc = s.getKey();
				pool.add(pool.add(pool.add(pool.add(w, desc.owner), desc.name), desc.param).put(desc.flags), s.getValue());
			}
		} else {
			w.putMedium(0);
		}

		if ((saveMode&2) != 0) {
			w.putVUInt(libSupers.size());
			for (Map.Entry<String, List<String>> s : libSupers.entrySet()) {
				pool.add(w, s.getKey());

				MapperList list = (MapperList) s.getValue();
				w.putVUInt(list.selfIdx).putVUInt(list.size());
				for (int i = 0; i < list.size(); i++) pool.add(w, list.get(i));
			}

			w.putVUInt(libStopAnchor.size());
			for (Desc s : libStopAnchor) {
				pool.add(pool.add(pool.add(w, s.owner), s.name), s.param);
			}
		} else {
			w.putShort(0);
		}

		try (var out = new LZMAOutputStream(cache, getLZMAOption(), -1)) {
			try (var header = new ByteList.ToStream(out, false)) {
				pool.writePool(header.putInt(FILE_HEADER));
			}
			w.writeToStream(out);
		}
	}

	public Boolean loadCache(InputStream cache, boolean readClassInheritanceMap) throws IOException {
		var r = MyDataInputStream.wrap(new LZMAInputStream(cache));

		if (r.readInt() != FILE_HEADER) throw new IllegalArgumentException("file header");

		var pool = new StringPool(r);

		int count = r.readVUInt();
		while(count-- > 0) classMap.put(pool.get(r), pool.get(r));

		count = r.readVUInt();
		fieldMap.ensureCapacity(count);
		while(count-- > 0) {
			fieldMap.put(new Desc(pool.get(r), pool.get(r), pool.get(r), r.readUnsignedByte()), pool.get(r));
		}

		count = r.readVUInt();
		methodMap.ensureCapacity(count);
		while(count-- > 0) {
			methodMap.put(new Desc(pool.get(r), pool.get(r), pool.get(r), r.readUnsignedByte()), pool.get(r));
		}

		if (!readClassInheritanceMap) return false;

		count = r.readVUInt();
		while(count-- > 0) {
			String name = pool.get(r);

			int idx = r.readVUInt();
			int len2 = r.readVUInt();
			MapperList sch = new MapperList(len2);
			while(len2-- > 0) sch.add(pool.get(r));
			sch.selfIdx = idx;
			sch.index();

			libSupers.put(name, sch);
		}

		count = r.readVUInt();
		while(count-- > 0) {
			libStopAnchor.add(new Desc(pool.get(r), pool.get(r), pool.get(r)));
		}

		return true;
	}

	// endregion
	/**
	 * 全量
	 */
	public void map(List<Context> ctxs) {
		if ((flag&MF_SINGLE_THREAD)!=0 || ctxs.size() <= ASYNC_THRESHOLD) {
			_map(ctxs, true);
			return;
		}

		stopAnchor = Collections.newSetFromMap(new ConcurrentHashMap<>(libStopAnchor.size()));
		stopAnchor.addAll(libStopAnchor);
		selfSupers = new ConcurrentHashMap<>(ctxs.size());
		selfInherited = new SynchronizedFindMap<>();

		List<List<Context>> tasks = new ArrayList<>((ctxs.size()-1)/ASYNC_THRESHOLD + 1);

		int i = 0;
		while (i < ctxs.size()) {
			int len = Math.min(ctxs.size()-i, ASYNC_THRESHOLD);
			tasks.add(ctxs.subList(i, i+len));
			i += len;
		}

		async(this::S1_parse, tasks);

		initSelfSuperMap();

		S2_begin(ctxs);

		if ((flag&MF_FIX_ACCESS) != 0) S2_1_FixAccess(ctxs, false);
		if ((flag&MF_FIX_SUBIMPL) != 0) S2_3_FixSubImpl(ctxs, false);

		S2_end();

		async((ctx) -> S3_mapSelf(ctx, false), tasks);
		async(this::S4_mapConstant, tasks);
		if ((flag&MF_RENAME_CLASS) != 0) {
			async(this::S5_mapClassName, tasks);
			async(this::S5_1_resetDebugInfo, tasks);
		}
	}

	private static void async(Consumer<Context> action, List<List<Context>> ctxs) {
		ArrayList<Future<?>> wait = new ArrayList<>(ctxs.size());
		for (int i = 0; i < ctxs.size(); i++) {
			List<Context> files = ctxs.get(i);
			var w = TaskPool.Common().submit(() -> {
				for (int j = 0; j < files.size(); j++) {
					try {
						action.accept(files.get(j));
					} catch (Throwable e) {
						throw new RuntimeException(files.get(j).getFileName(), e);
					}
				}
				return null;
			});
			wait.add(w);
		}

		for (int i = 0; i < wait.size(); i++) {
			try {
				wait.get(i).get();
			} catch (InterruptedException ignored) {
			} catch (ExecutionException e) {
				Helpers.athrow(e.getCause());
			}
		}
	}

	/**
	 * 增量
	 */
	public void mapIncr(List<Context> ctxs) { _map(ctxs, false); }

	private void _map(List<Context> ctxs, boolean full) {
		if (selfSupers == null || full) initSelf(ctxs.size());

		Context ctx = null;
		try {
			MyHashSet<String> modified = full?null:new MyHashSet<>();
			for (int i = 0; i < ctxs.size(); i++) {
				S1_parse(ctx = ctxs.get(i));
				if (!full) modified.add(ctx.getData().name);
			}

			initSelfSuperMap();
			if (!full) {
				Predicate<Desc> rem = key -> modified.contains(key.owner);
				selfInherited.keySet().removeIf(rem);
				stopAnchor.removeIf(rem);
			}

			S2_begin(ctxs);

			if ((flag&MF_FIX_ACCESS) != 0) S2_1_FixAccess(ctxs, false);
			if ((flag&MF_FIX_SUBIMPL) != 0) S2_3_FixSubImpl(ctxs, false);

			S2_end();

			for (int i = 0; i < ctxs.size(); i++) S3_mapSelf(ctx = ctxs.get(i), false);
			for (int i = 0; i < ctxs.size(); i++) S4_mapConstant(ctx = ctxs.get(i));
			if ((flag&MF_RENAME_CLASS) != 0) {
				for (int i = 0; i < ctxs.size(); i++) {
					S5_mapClassName(ctx = ctxs.get(i));
					S5_1_resetDebugInfo(ctx);
				}
			}
		} catch (Throwable e) {
			throw new RuntimeException("At parsing " + ctx, e);
		}
	}

	// region 映射

	/**
	 * Step 1 Prepare parent mapping
	 */
	public final void S1_parse(Context c) {
		ConstantData data = c.getData();
		var itfs = data.interfaces();

		int size = itfs.size() + ("java/lang/Object".equals(data.parent) ? 0 : 1);
		if (size == 0 && (flag&MF_ANNOTATION_INHERIT) == 0) return;

		ArrayList<String> list = new ArrayList<>(size);
		if (!"java/lang/Object".equals(data.parent)) list.add(data.parent);
		for (int i = 0; i < itfs.size(); i++) list.add(itfs.get(i));

		if ((flag&MF_ANNOTATION_INHERIT) != 0) {
			var found = Annotation.findInvisible(data.cp, data, "roj/mapper/Inherited");
			if (found != null) {
				for (AnnVal klass : found.getArray("value")) {
					list.add(klass.asClass().owner);
				}
			}
			if (list.isEmpty()) return;
		}

		selfSupers.put(data.name, list);
	}

	private MyHashMap<String, IClass> s2_tmp_byName;
	private MyHashMap<String, List<Desc>> s2_tmp_methods;
	public final void S2_begin(List<Context> ctxs) {
		MyHashMap<String, IClass> classInfo = new MyHashMap<>(ctxs.size());
		for (int i = 0; i < ctxs.size(); i++) {
			ConstantData data = ctxs.get(i).getData();
			classInfo.put(data.name, data);
		}
		s2_tmp_byName = classInfo;
		s2_tmp_methods = new MyHashMap<>();
	}

	/**
	 * Step 2.1 (Optional) Fix access bug when changing package
	 * @param downgradeToo downgrade access level also (no supported yet)
	 */
	public final void S2_1_FixAccess(List<Context> ctxs, boolean downgradeToo) {
		MyHashSet<Desc> upgraded = new MyHashSet<>();
		MyHashSet<Object> processed = new MyHashSet<>();

		for (int i = 0; i < ctxs.size(); i++) {
			Context ctx = ctxs.get(i);

			String selfName = ctx.getData().name;
			String selfNewName = classMap.getOrDefault(selfName, selfName);

			List<CstClass> cref = ctx.getClassConstants();
			for (int j = 0; j < cref.size(); j++) {
				String name = cref.get(j).name().str();
				if (name.equals(selfName)) continue;

				if (processed.contains(name)) continue;

				IClass cn = s2_tmp_byName.get(name);
				//SIG:reflectClassInfo
				if (cn == null) continue;

				if ((cn.modifier()&ACC_PUBLIC) != 0) continue;

				String newName = classMap.getOrDefault(name, name);
				if (!ClassUtil.arePackagesSame(selfNewName, newName)) {
					cn.modifier(cn.modifier()|ACC_PUBLIC);
					processed.add(name);

					LOGGER.log(Level.TRACE, "[FAcc-C] 提升了 {} 引用的 {} (+public)", null, selfName, name);
				}
			}

			checkAccess(ctx.getMethodConstants(), true, processed, selfName, selfNewName, upgraded);
			checkAccess(ctx.getFieldConstants(), false, processed, selfName, selfNewName, upgraded);

			ConstantData data = ctx.getData();
			SimpleList<MethodNode> methods = data.methods;
			for (int j = 0; j < methods.size(); j++) {
				MethodNode mn = methods.get(j);
				// inheritable package-private method
				if ((mn.modifier() & (ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE|ACC_STATIC|ACC_FINAL)) == 0) {
					upgraded.add(new Desc(data.name, mn.name(), mn.rawDesc(), 6));
				}
			}
		}

		Desc d = ClassUtil.getInstance().sharedDC;
		List<String> exist = new SimpleList<>();
		for (int i = 0; i < ctxs.size(); i++) {
			ConstantData data = ctxs.get(i).getData();

			List<String> parents = selfSupers.get(data.name);
			if (parents == null) continue;

			exist.clear();
			for (int j = 0; j < parents.size(); j++) {
				String parent = parents.get(j);
				if (s2_tmp_byName.containsKey(parent)) exist.add(parent);
			}
			if (exist.isEmpty()) continue;

			SimpleList<MethodNode> methods = data.methods;
			for (int j = 0; j < methods.size(); j++) {
				MethodNode mn = methods.get(j);

				d.owner = data.name;
				d.name = mn.name();
				d.param = mn.rawDesc();
				if (d.name.startsWith("<")) continue;

				int k = 0;
				while (true) {
					Desc d1 = upgraded.find(d);
					if (d1 != d) {
						char acc = mn.modifier();
						boolean isProtected = (d1.flags& ACC_PUBLIC) == 0;
						boolean isInherited = d1.flags == 6;
						if (isProtected) {
							if ((acc&(ACC_PUBLIC|ACC_PROTECTED)) == 0) {
								mn.modifier(acc|ACC_PROTECTED);
								LOGGER.log(Level.TRACE, "[FAcc-I] 提升了 {} 继承的 {} (+protected)", null, data.name, d);
							}
						} else {
							if ((acc& ACC_PUBLIC) == 0) {
								mn.modifier(acc & ~ACC_PROTECTED | ACC_PUBLIC);
								LOGGER.log(Level.TRACE, "[FAcc-I] 提升了 {} 继承的 {} (+public)", null, data.name, d);
							}
						}

						if (isInherited) {
							IClass ctx = s2_tmp_byName.get(d1.owner);
							RawNode m = ctx.methods().get(ctx.getMethod(d1.name, d1.param));
							if ((m.modifier()&5) == 0) m.modifier(m.modifier()|ACC_PROTECTED);
							d1.flags = 0;
						}
					}

					if (stopAnchor.contains(d)) break;

					if (k == exist.size()) break;
					d.owner = exist.get(k++);
				}
			}
		}
	}
	private void checkAccess(List<CstRef> ref, boolean method,
							 MyHashSet<Object> processed,
							 String selfName, String selfNewName,
							 MyHashSet<Desc> upgraded) {
		Desc d = ClassUtil.getInstance().sharedDC;
		nextNode:
		for (int j = 0; j < ref.size(); j++) {
			if (processed.contains(ref.get(j))) continue;
			d.read(ref.get(j));

			RawNode node;

			int k = 0;
			List<String> parents = selfSupers.getOrDefault(d.owner, Collections.emptyList());

			found:
			while (true) {
				//SIG:reflectClassInfo | getMethodInfo
				IClass aa = s2_tmp_byName.get(d.owner);
				if (aa != null) {
					List<RawNode> methods = Helpers.cast(method?aa.methods():aa.fields());
					for (int i1 = 0; i1 < methods.size(); i1++) {
						node = methods.get(i1);
						if (node.rawDesc().equals(d.param) && node.name().equals(d.name)) {
							break found;
						}
					}
				}

				if (stopAnchor.contains(d) || k == parents.size()) {
					processed.add(ref.get(j));
					continue nextNode;
				}

				d.owner = parents.get(k++);
			}

			char acc = node.modifier();
			if ((acc & (ACC_PUBLIC|ACC_PRIVATE)) != 0) {
				processed.add(ref.get(j));
				continue; // public
			}

			String newName = classMap.getOrDefault(d.owner, d.owner);
			if (ClassUtil.arePackagesSame(newName, selfNewName)) continue; // package-private

			//SIG:不检测class: protected的判断太麻烦了，还要检测是不是aload_0
			boolean protectedEnough = selfName.equals(ref.get(j).className());
			if ((acc & ACC_PROTECTED) == 0 || !protectedEnough) {
				LOGGER.log(Level.TRACE, "[FAcc-N] 提升了 {} 引用的 {} (+{})", null, selfName, d, protectedEnough ? "protected" : "public");
				if (protectedEnough) {
					node.modifier(acc|ACC_PROTECTED);
				} else {
					processed.add(ref.get(j));
					node.modifier(acc & ~ACC_PROTECTED | ACC_PUBLIC);
				}

				if (method && (acc & (ACC_STATIC|ACC_FINAL)) == 0) {
					d.flags = node.modifier();
					upgraded.add(d.copy());
				}
			}
		}
	}

	/**
	 * Step 2.2 (Optional) Fix mapping on inheritance chain result in same name method <br>
	 * The mapping must be modifiable
	 */
	public final boolean S2_2_FixInheritConflict(List<Context> ctxs) {
		MyHashMap<NameAndType, Set<NameAndType>> sameNameNodes = new MyHashMap<>();
		MyHashSet<String> checked = new MyHashSet<>();

		boolean success = true;
		for (Context ctx : ctxs) {
			ConstantData data = ctx.getData();
			if (!checked.add(data.name)) continue;

			tryMap(null, data.name, data.fields, sameNameNodes, fieldMap);
			tryMap(null, data.name, data.methods, sameNameNodes, methodMap);

			List<String> parents = selfSupers.getOrDefault(data.name, Collections.emptyList());
			for (String parent : parents) {
				checked.add(parent);
				tryMap(data.name, parent, getMethodInfoEx(parent), sameNameNodes, methodMap);
			}

			for (Map.Entry<NameAndType, Set<NameAndType>> entry : sameNameNodes.entrySet()) {
				if (entry.getValue().size() <= 1) continue;

				success = false;
				LOGGER.log(Level.WARN, "[InheritConflict]: {} => {}", null, entry.getValue(), entry.getKey().name);

				boolean remove = false;
				for (NameAndType desc : entry.getValue()) {
					if (desc.flags == 0) {
						// unmappable
						remove = true;
						break;
					}
				}

				for (NameAndType desc : entry.getValue()) {
					if (remove) methodMap.remove(desc.copy());
					remove = true;
				}
			}
			sameNameNodes.clear();
		}
		return success;
	}
	private void tryMap(String inheritTo,
						String owner, List<?> list,
						MyHashMap<NameAndType, Set<NameAndType>> descs,
						FindMap<Desc, String> map) {
		List<String> parents = selfSupers.getOrDefault(owner, Collections.emptyList());
		Desc d = ClassUtil.getInstance().sharedDC;
		for (int i = 0; i < list.size(); i++) {
			RawNode m = (RawNode) list.get(i);

			d.owner = owner;
			d.name = m.name();
			d.param = m.rawDesc();
			d.flags = 0;

			if (inheritTo != null) {
				char acc = m.modifier();
				if ((acc & (ACC_PRIVATE)) != 0) continue;
				if ((acc & (ACC_PUBLIC|ACC_PROTECTED)) == 0 && !ClassUtil.arePackagesSame(inheritTo, owner)) continue;
			}

			int j = 0;
			while (true) {
				Map.Entry<Desc, String> entry = map.find(d);
				if (entry != null) {
					d.name = entry.getValue();
					d.flags = 1;
					break;
				}

				if (j == parents.size()) break;
				d.owner = parents.get(j++);

				if (stopAnchor.contains(d)) {
					//SIG:DEBUG
					LOGGER.log(Level.DEBUG, "[debug] stop on {}", null, d);
					break;
				}
			}

			NameAndType key = new NameAndType();
			key.name = d.name;
			key.param = d.param;

			NameAndType val = new NameAndType();
			val.owner = d.owner;
			val.name = m.name();
			val.param = key.param;
			val.flags = d.flags;
			descs.computeIfAbsent(key, Helpers.fnMyHashSet()).add(val);
		}
	}

	/**
	 * Step 2.3 (Optional) 修复父类的方法被子类实现的接口使用时的映射冲突 <BR>
	 *     <p>"Implements method in <i>some interface</i> via subclass"</p>
	 * @param mapIsMutable allow to remove mapping
	 * @return added mapping
	 */
	public final List<Desc> S2_3_FixSubImpl(List<Context> ctxs, boolean mapIsMutable) {
		List<Desc> added = new SimpleList<>();

		for (SubImpl method : collectSubImpl(ctxs)) {
			Desc desc = method.type.copy();

			for (Set<String> classList : method.owners) {
				if (classList.remove(null)) {
					if (!mapIsMutable) throw new IllegalArgumentException("SubImpl映射失败: 不可变: " + classList + " of " + method.type);

					for (String owner : classList) {
						desc.owner = owner;
						methodMap.remove(desc);
					}

					continue;
				}

				String mapName = null, mapClass = null;
				for (String owner : classList) {
					desc.owner = owner;

					String name = methodMap.get(desc);
					if (name != null) {
						if (mapName == null) {
							mapClass = owner;
							mapName = name;
							if (mapIsMutable) break;
						} else if (!mapName.equals(name)) {
							throw new IllegalStateException("SubImpl映射失败: 映射名称不同: " + classList + " of " + method.type);
						}
					}
				}

				assert mapName != null;
				for (String owner : classList) {
					if (owner.equals(mapClass)) continue;
					desc.owner = owner;

					if (!mapName.equals(methodMap.put(desc, mapName))) {
						added.add(desc);

						ConstantData data = (ConstantData) s2_tmp_byName.get(desc.owner);
						int i = data.getMethod(desc.name, desc.param);
						if (i < 0) throw new IllegalStateException("缺少元素(not in context...): " + desc);
						desc.flags = data.methods.get(i).modifier();

						desc = desc.copy();
					}
				}
			}
		}
		return added;
	}
	private Set<SubImpl> collectSubImpl(List<Context> ctx) {
		ClassUtil U = ClassUtil.getInstance();

		MyHashSet<SubImpl> out = new MyHashSet<>();

		SubImpl sTest = new SubImpl();
		NameAndType nTest = new NameAndType();
		Desc dTest = U.sharedDC;

		SimpleList<NameAndType> interfaceMethods = new SimpleList<>();

		for (int i = 0; i < ctx.size(); i++) {
			ConstantData data = ctx.get(i).getData();
			if ((data.modifier() & (ACC_INTERFACE|ACC_ANNOTATION|ACC_MODULE)) != 0) continue;

			List<String> itfs = data.interfaces();
			if (itfs.isEmpty()) continue;

			interfaceMethods.clear();

			List<String> parents = selfSupers.get(data.parent);
			if (parents == null) {
				ReflectClass rc = ClassUtil.reflectClassInfo(data.parent);
				parents = rc == null ? Collections.emptyList() : rc.i_superClassAll();
			}

			for (int j = 0; j < itfs.size(); j++) {
				String name = itfs.get(j);
				if (!parents.contains(name)) {
					List<RawNode> nodes = getMethodInfoEx(name);

					for (int k = 0; k < nodes.size(); k++) {
						RawNode node = nodes.get(k);
						if ((node.modifier() & (ACC_PRIVATE|ACC_STATIC)) != 0) continue;

						NameAndType key = new NameAndType();
						key.owner = name;
						key.name = node.name();
						key.param = node.rawDesc();
						interfaceMethods.add(key);
					}
				}
			}
			if (interfaceMethods.isEmpty()) continue;

			int j = 0;
			String parent = data.parent;
			while (true) {
				List<RawNode> nodes = getMethodInfoEx(parent);
				for (int k = 0; k < nodes.size(); k++) {
					RawNode node = nodes.get(k);
					if ((node.modifier() & (ACC_PUBLIC|ACC_STATIC)) != ACC_PUBLIC) continue;

					if ((nTest.name = node.name()).startsWith("<")) continue;
					nTest.param = node.rawDesc();

					int id = -1;
					while ((id = interfaceMethods.indexOf(nTest, id+1)) >= 0) {
						NameAndType issuer = interfaceMethods.get(id);

						dTest.owner = issuer.owner;
						dTest.name = issuer.name;
						dTest.param = issuer.param;

						if (!methodMap.containsKey(dTest)) {
							dTest.owner = parent;
							if (!methodMap.containsKey(dTest)) continue;
						}

						sTest.type = issuer;
						SubImpl s_get = out.intern(sTest);
						if (s_get == sTest) sTest = new SubImpl();

						Set<String> set;
						found: {
							for (Iterator<Set<String>> itr = s_get.owners.iterator(); itr.hasNext(); ) {
								set = itr.next();
								if (set.contains(parent) || set.contains(issuer.owner)) break found;
							}

							set = new MyHashSet<>(2);
							s_get.owners.add(set);
						}

						set.add(parent);
						set.add(issuer.owner);

						// native不能
						if ((node.modifier() & ACC_NATIVE) != 0) set.add(null);
						// 至少有一个类不是要处理的类: 不能混淆
						if (!s2_tmp_byName.containsKey(parent) || !s2_tmp_byName.containsKey(issuer.owner)) set.add(null);
					}
				}

				if (j == parents.size()) break;
				parent = parents.get(j++);
			}
		}

		return out;
	}
	@NotNull
	private List<RawNode> getMethodInfoEx(String name) {
		IClass c = s2_tmp_byName.get(name);
		if (c == null) c = ClassUtil.reflectClassInfo(name);
		if (c != null) return Helpers.cast(c.methods());

		if (s2_tmp_methods.isEmpty()) {
			for (Desc key : methodMap.keySet()) {
				s2_tmp_methods.computeIfAbsent(key.owner, Helpers.fnArrayList()).add(key);
			}
		}
		List<Desc> list = s2_tmp_methods.getOrDefault(name, Collections.emptyList());
		for (int i = 0; i < list.size(); i++) {
			Desc desc = list.get(i);
			if (desc.flags == Desc.FLAG_UNSET) throw new IllegalStateException("缺少元素: "+desc);
		}
		return Helpers.cast(list);
	}

	public final void S2_end() { s2_tmp_byName = null; s2_tmp_methods = null; }

	/**
	 * Step 3 Self method/field name (and type in record)
	 */
	public final void S3_mapSelf(Context ctx, boolean simulate) {
		ConstantData data = ctx.getData();
		data.unparsed();

		List<String> parents = selfSupers.getOrDefault(data.name, Collections.emptyList());

		Desc d = ClassUtil.getInstance().sharedDC;

		List<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode m = methods.get(i);

			d.owner = data.name;
			d.name = m.name();
			d.param = m.rawDesc();

			int j = 0;
			while (true) {
				Map.Entry<Desc, String> entry = methodMap.find(d);
				add_stop_anchor:
				if (entry == null) {
					if (j == parents.size()) break;
					d.owner = parents.get(j++);
					continue;
				} else {
					int acc = entry.getKey().flags;
					if (acc == Desc.FLAG_UNSET) throw new IllegalStateException("缺少元素: " + entry.getKey());

					boolean childrenCannotInherit = false;

					// 无法被继承
					if (0 != (acc & (ACC_STATIC|ACC_PRIVATE|ACC_FINAL))) {
						// j == 0 <==> d.owner == data.name (library only)
						if (j > 0 || simulate) break add_stop_anchor;
						// 自己的方法
						childrenCannotInherit = true;
					} else if (0 == (acc & (ACC_PUBLIC|ACC_PROTECTED)) &&
								!ClassUtil.arePackagesSame(data.name, d.owner)) {
						// package-private
						break add_stop_anchor;
					}

					String newName = entry.getValue();
					if (!simulate) {
						m.name(newName);
						d.owner = data.name;
						// fast-path ONLY
						selfInherited.put(d.copy(), newName);
					}

					if (childrenCannotInherit) break add_stop_anchor;

					break;
				}

				d.owner = data.name;
				stopAnchor.add(d.copy());
				LOGGER.log(Level.TRACE, "[M-S][{}]: {}.{}{}", null, data.name, j==0?"~":d.owner, d.name, d.param);
				break;
			}
		}

		d.param = "";

		List<? extends RawNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode f = (FieldNode) fields.get(i);

			d.owner = data.name;
			d.name = f.name();
			if (checkFieldType) d.param = f.rawDesc();

			int j = 0;
			while (true) {
				String newName = fieldMap.get(d);
				if (newName != null) {
					// j == 0 <==> d.owner == data.name (library only)
					if (j > 0) {
						d.owner = data.name;
						stopAnchor.add(d.copy());
						LOGGER.log(Level.TRACE, "[F-S][{}]: {}.{} {}", null, data.name, d.owner, d.name, d.param);
					} else {
						if (!simulate) f.name(newName);
					}
					break;
				}

				if (j == parents.size()) break;
				d.owner = parents.get(j++);
			}
		}

		if (!simulate) mapRecord(d, data);
	}
	/** Field name and type in 'Record' attribute */
	private void mapRecord(Desc d, ConstantData data) {
		AttrRecord r = data.parsedAttr(data.cp, Attribute.Record);
		if (r == null) return;

		ClassUtil U = ClassUtil.getInstance();

		d.owner = data.name;
		d.param = "";
		List<AttrRecord.Val> vars = r.variables;
		for (int i = 0; i < vars.size(); i++) {
			AttrRecord.Val v = vars.get(i);

			d.name = v.name;
			if (checkFieldType) d.param = v.type;

			String newName = fieldMap.get(d);
			if (newName != null) {
				v.name = newName;
			}

			String newCls = U.mapFieldType(classMap, v.type);
			if (newCls != null) v.type = newCls;
		}
	}

	/**
	 * Step 4: Reference
	 */
	public final void S4_mapConstant(Context ctx) {
		ConstantData data = ctx.getData();

		BootstrapMethods bs = null;

		List<Constant> list = data.cp.array();
		for (int j = 0; j < list.size(); j++) {
			Constant c = list.get(j);
			switch (c.type()) {
				case Constant.INTERFACE: case Constant.METHOD:
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
	/** Map: lambda method name */
	private void mapLambda(BootstrapMethods bs, ConstantData data, CstDynamic dyn) {
		if (dyn.tableIdx >= bs.methods.size())
			throw new IllegalArgumentException("BootstrapMethod id 不存在: "+(int) dyn.tableIdx+" at class "+data.name);

		BootstrapMethods.Item ibm = bs.methods.get(dyn.tableIdx);
		if (!ibm.isInvokeMethod()) return;

		Desc d = ClassUtil.getInstance().sharedDC;

		d.name = dyn.desc().name().str();
		// FP: init / clinit
		if (d.name.startsWith("<")) return;

		String allDesc = dyn.desc().getType().str();
		if (!allDesc.endsWith(";")) return;

		d.param = ibm.interfaceDesc();
		d.owner = TypeHelper.parseReturn(allDesc).owner;

		List<String> parents = selfSupers.getOrDefault(d.owner, Collections.emptyList());
		int i = 0;
		while (true) {
			String name = methodMap.get(d);
			if (name != null) {
				dyn.setDesc(data.cp.getDesc(name, allDesc));
				return;
			}

			if (stopAnchor.contains(d)) {
				LOGGER.log(Level.TRACE, "[L-S][{}]: {}.{}{}", null, data.name, i==0?"~":d.owner, d.name, d.param);
				break;
			}

			if (i == parents.size()) break;
			d.owner = parents.get(i++);
		}
	}
	/** method/field reference */
	private void mapRef(ConstantData data, CstRef ref, boolean method) {
		Desc d = ClassUtil.getInstance().sharedDC;
		d.read(ref);

		if (method) {
			// FP: init / clinit
			if (d.name.startsWith("<")) return;

			// FP: Stage 2
			String fpName = selfInherited.get(d);

			if (fpName != null) {
				setRefName(data, ref, fpName);
				return;
			}
		} else {
			if (!checkFieldType) d.param = "";
		}

		FindMap<Desc, String> map = method ? methodMap : fieldMap;
		List<String> parents = selfSupers.getOrDefault(d.owner, Collections.emptyList());
		int i = 0;
		while (true) {
			String name = map.get(d);
			if (name != null) {
				setRefName(data, ref, name);

				// fix field inherit bug
				if (i > 0 && !method) ref.clazz(data.cp.getClazz(d.owner));
				break;
			}

			// 是放在这里，因为d.owner一开始是data.name
			if (stopAnchor.contains(d)) {
				LOGGER.log(Level.TRACE, method?"[R-M-S][{}]: {}.{}{}":"[R-F-S][{}]: {}.{} {}", null, data.name, d.owner, d.name, d.param);
				break;
			}

			if (i == parents.size()) break;
			d.owner = parents.get(i++);
		}
	}

	/**
	 * Step 5: (Optional) Dedicated class name and annotation 'value' name
	 */
	public final void S5_mapClassName(Context ctx) {
		ClassUtil U = ClassUtil.getInstance();
		ConstantData data = ctx.getData();

		mapInnerClass(U, data);
		mapSignature(data.cp, data);
		mapNodeAndAttrAndParam(U, data);
		mapAnnotations(U, data.cp, data);

		data.unparsed(); // serialize to cp
		mapConstant(U, data.cp);
		mapClassAndSuper(data);
	}
	/** InnerClass type */
	private void mapInnerClass(ClassUtil U, ConstantData data) {
		var classes = data.getInnerClasses();
		CharList sb = IOUtil.getSharedCharBuf();
		for (int j = 0; j < classes.size(); j++) {
			InnerClasses.Item clz = classes.get(j);
			if (clz.parent != null) {
				sb.clear();
				String name = U.mapClassName(classMap, sb.append(clz.parent).append('$').append(clz.name));
				if (name != null) {
					int i = name.lastIndexOf('$');
					if (i <= 0 || i == name.length()-1) {
						LOGGER.log(Level.DEBUG, "[InnerClass]: {}${} => {}", null, clz.parent, clz.name, name);
						clz.name = name;
					} else {
						clz.name = name.substring(i+1);
					}
				}

				name = U.mapClassName(classMap, clz.parent);
				if (name != null) clz.parent = name;
			}

			String name = U.mapClassName(classMap, clz.self);
			if (name != null) clz.self = name;
		}
	}
	/** Annotation type and field key */
	private void mapAnnotations(ClassUtil U, ConstantPool cp, Attributed node) {
		AsmShared ash = AsmShared.local();

		Attribute a = node.attrByName("RuntimeVisibleAnnotations");
		if (a != null) mapAnnotations(U, cp, ash.copy(a.getRawData()));
		a = node.attrByName("RuntimeInvisibleAnnotations");
		if (a != null) mapAnnotations(U, cp, ash.copy(a.getRawData()));
	}
	private void mapAnnotations(ClassUtil U, ConstantPool cp, DynByteBuf r) {
		int len = r.readUnsignedShort();
		while (len-- > 0) mapAnnotation(U, cp, r);
	}
	private void mapAnnotation(ClassUtil U, ConstantPool cp, DynByteBuf r) {
		CstUTF owner = (CstUTF) cp.get(r);
		String newOwner = U.mapFieldType(classMap, owner.str());
		if (newOwner != null) r.putShort(r.rIndex-2, cp.getUtfId(newOwner));

		String owner_name = owner.str().substring(1, owner.str().length()-1);
		int len = r.readUnsignedShort();
		while (len-- > 0) {
			String name = ((CstUTF) cp.get(r)).str();

			// assert is annotation class...
			for (Map.Entry<Desc, String> entry : methodMap.entrySet()) {
				if (entry.getKey().name.equals(name) && entry.getKey().owner.equals(owner_name)) {
					r.putShort(r.rIndex-2, cp.getUtfId(entry.getValue()));
					break;
				}
			}

			mapAnnotationNode(U, cp, r);
		}
	}
	private void mapAnnotationNode(ClassUtil U, ConstantPool cp, DynByteBuf r) {
		switch (r.readUnsignedByte()) {
			default: r.rIndex += 2; break;
			case AnnVal.ANNOTATION_CLASS: {
				CstUTF owner = (CstUTF) cp.get(r);
				String newOwner = U.mapFieldType(classMap, owner.str());
				if (newOwner != null) {
					r.putShort(r.rIndex-2, cp.getUtfId(newOwner));
				}
			}
			break;
			case AnnVal.ENUM: {
				CstUTF owner = (CstUTF) cp.get(r);
				String newOwner = U.mapFieldType(classMap, owner.str());
				if (newOwner != null) r.putShort(r.rIndex-2, cp.getUtfId(newOwner));

				CstUTF enum_name = (CstUTF) cp.get(r);

				Desc fd = U.sharedDC;
				// old name
				fd.owner = owner.str().substring(1,owner.str().length()-1);
				fd.name = enum_name.str();
				fd.param = "";

				String newFieldName = fieldMap.get(fd);

				if (checkFieldType) {
					for (Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
						if (entry.getKey().name.equals(fd.name) && entry.getKey().owner.equals(fd.owner)) {
							if (newFieldName != null) throw new RuntimeException("Duplicate field name in same (enum) class "+fd);
							newFieldName = entry.getValue();
						}
					}
				}

				if (newFieldName != null) {
					r.putShort(r.rIndex-2, cp.getUtfId(newFieldName));
				}
			}
			break;
			case AnnVal.ANNOTATION: mapAnnotation(U, cp, r); break;
			case ARRAY:
				int len = r.readUnsignedShort();
				while (len-- > 0) mapAnnotationNode(U, cp, r);
				break;
		}
	}
	/** Generic signature type */
	private void mapSignature(ConstantPool pool, Attributed node) {
		Signature generic = node.parsedAttr(pool, Attribute.SIGNATURE);
		if (generic != null) generic.rename(GENERIC_TYPE_MAPPER);
	}
	/** Class name and parent */
	private void mapClassAndSuper(ConstantData data) {
		data.name(data.name());
		data.parent(data.parent());
	}
	/** Method/Field type, its signature, annotation and method parameter type */
	private void mapNodeAndAttrAndParam(ClassUtil U, ConstantData data) {
		String oldCls, newCls;

		List<CNode> nodes = Helpers.cast(data.fields);
		for (int i = 0; i < nodes.size(); i++) {
			CNode field = nodes.get(i);

			oldCls = field.rawDesc();
			newCls = U.mapFieldType(classMap, oldCls);

			if (newCls != null) field.rawDesc(newCls);

			mapSignature(data.cp, field);
			mapAnnotations(U, data.cp, field);
		}

		nodes = Helpers.cast(data.methods);
		for (int i = 0; i < nodes.size(); i++) {
			CNode method = nodes.get(i);

			oldCls = method.rawDesc();
			newCls = U.mapMethodParam(classMap, oldCls);

			if (!oldCls.equals(newCls)) method.rawDesc(newCls);

			mapSignature(data.cp, method);
			mapAnnotations(U, data.cp, method);
		}

		for (int i = 0; i < nodes.size(); i++) {
			PARAM_TYPE_MAPPER.mapParam(data.cp, (MethodNode) nodes.get(i));
		}
	}
	/** Any other: interface, bootstrap method, class ref... */
	private void mapConstant(ClassUtil U, ConstantPool cp) {
		String oldCls, newCls;
		List<Constant> arr = cp.array();
		for (int i = 0; i < arr.size(); i++) {
			Constant c = arr.get(i);
			switch (c.type()) {
				case Constant.NAME_AND_TYPE: {
					CstNameAndType ref = (CstNameAndType) c;

					oldCls = ref.getType().str();
					newCls = oldCls.startsWith("(")?U.mapMethodParam(classMap, oldCls):U.mapFieldType(classMap, oldCls);

					if (newCls != null && !newCls.equals(oldCls)) ref.setType(cp.getUtf(newCls));
				}
				break;
				case Constant.CLASS: {
					CstClass clazz = (CstClass) c;

					oldCls = clazz.name().str();
					newCls = U.mapClassName(classMap, oldCls);

					if (newCls != null) clazz.setValue(cp.getUtf(newCls));
				}
				break;
				case Constant.METHOD_TYPE: {
					CstMethodType type = (CstMethodType) c;

					oldCls = type.name().str();
					newCls = U.mapMethodParam(classMap, oldCls);

					if (!oldCls.equals(newCls)) type.setValue(cp.getUtf(newCls));
				}
				break;
			}
		}
	}

	public final void S5_1_resetDebugInfo(Context ctx) {
		ConstantData data = ctx.getData();
		AttrString sourceFile = data.parsedAttr(data.cp, Attribute.SourceFile);
		if (sourceFile != null) {
			String name = data.name;
			sourceFile.value = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('$'))+1).concat(".java");
		}
	}
	// endregion
	// region libraries
	public void loadLibraries(List<?> files) {
		SimpleList<Context> classes = new SimpleList<>();
		Desc m = ClassUtil.getInstance().sharedDC;

		var prevSS = selfSupers;
		var prevSA = stopAnchor;

		selfSupers = libSupers;
		stopAnchor = libStopAnchor;

		for (int i = 0; i < files.size(); i++) {
			Object o = files.get(i);
			if (o instanceof File fi) {
				String f = fi.getName().toLowerCase(Locale.ROOT);
				if (!f.startsWith(DONT_LOAD_PREFIX) && (f.endsWith(".zip") || f.endsWith(".jar"))) {
					try (ZipFile archive = new ZipFile(fi)) {
						for (ZEntry entry : archive.entries()) {
							if (entry.getName().endsWith(".class")) {
								try (InputStream in = archive.getStream(entry)) {
									readLibFile(new Context(entry.getName(), in), classes, m);
								} catch (Throwable e) {
									Terminal.warning(f+"#!"+entry.getName()+" 无法读取", e);
								}
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} else {
				List<Context> ctx = Helpers.cast(o);
				for (int j = 0; j < ctx.size(); j++) {
					readLibFile(ctx.get(j), classes, m);
				}
			}
		}

		makeInheritMap(libSupers, (flag & FLAG_FULL_CLASS_MAP) != 0 ? classMap : null);

		// compute stop anchors
		for (int i = 0; i < classes.size(); i++) S3_mapSelf(classes.get(i), true);

		selfSupers = prevSS;
		stopAnchor = prevSA;
	}
	private void readLibFile(Context ctx, List<Context> classes, Desc d) {
		classes.add(ctx);
		S1_parse(ctx);

		ConstantData data = ctx.getData();
		if ((flag & FLAG_FULL_CLASS_MAP) != 0 && !classMap.containsKey(data.name())) return;

		// 更新访问权限
		d.owner = data.name();
		findAndReplace(methodMap, d, data.methods(), true);
		d.param = "";
		findAndReplace(fieldMap, d, data.fields(), checkFieldType);
	}
	private void findAndReplace(FindMap<Desc, String> flags, Desc d, List<? extends RawNode> nodes, boolean par) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode n = nodes.get(i);
			d.name = n.name();
			if (par) d.param = n.rawDesc();

			Map.Entry<Desc, String> entry = flags.find(d);
			if (entry != null) entry.getKey().flags = n.modifier();
		}
	}

	/**
	 * 加载完所有的库之后调用此函数来完成Mapper的准备 <br>
	 * 不调用此方法进行映射的行为是<i>未定义</i>的
	 */
	public Mapper packup() {
		if (classNameChanged()) flag |= MF_RENAME_CLASS;
		else flag &= ~MF_RENAME_CLASS;

		Desc m = ClassUtil.getInstance().sharedDC;

		// check inherit & overloads
		for (Desc d : new SimpleList<>(methodMap.keySet())) {
			List<String> parents = libSupers.get(d.owner);
			if (parents == null) continue;

			m.name = d.name;
			m.param = d.param;

			Map.Entry<Desc, String> prev = null, entry;
			for (int i = parents.size()-1; i >= -1; i--) {
				m.owner = i < 0 ? d.owner : parents.get(i);

				entry = methodMap.find(m);
				if (entry != null) {
					if (prev == null) {
						prev = entry;
					} else if (prev.getValue().equals(entry.getValue())) {
						if ((flag&FLAG_FIX_INHERIT) != 0) methodMap.remove(m);
					} else {
						if ((flag&FLAG_FIX_INHERIT) == 0) LOGGER.log(Level.WARN, "[Packup]: 映射继承冲突: [{}|{}].{}{}", null, m.owner, d.owner, m.name, m.param);
						methodMap.remove(m);
					}
				}

				if (libStopAnchor.contains(m)) prev = null;
			}
		}

		MyHashSet<Desc> unmatched = new MyHashSet<>();
		for (Desc desc : methodMap.keySet())
			if (desc.flags == Desc.FLAG_UNSET)
				unmatched.add(desc);
		for (Desc desc : fieldMap.keySet())
			if (desc.flags == Desc.FLAG_UNSET)
				unmatched.add(desc);

		if (!unmatched.isEmpty()) {
			LOGGER.log(Level.WARN, "[Packup]: 缺少元素({}): {}...", null, unmatched.size(), (DEBUG?unmatched:unmatched.iterator().next()));
			// Stage2如果用到了这些元素会报错
		}

		return this;
	}
	// endregion

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
		libStopAnchor.clear();
	}

	public final void initSelfSuperMap() {
		Map<String, List<String>> universe = new MyHashMap<>(libSupers);
		for (int i = 0; i < extraStates.size(); i++) {
			State state = extraStates.get(i);
			universe.putAll(state.parents);
			stopAnchor.addAll(state.stopAnchor);
		}
		universe.putAll(selfSupers); // replace lib class
		selfSupers = universe;

		makeInheritMap(universe, (flag & (FLAG_FULL_CLASS_MAP|MF_FIX_SUBIMPL)) == FLAG_FULL_CLASS_MAP ? classMap : null);
	}

	public final void initSelf(int size) {
		stopAnchor = new MyHashSet<>(libStopAnchor);
		selfSupers = new MyHashMap<>(size);
		selfInherited = new MyHashMap<>();
	}

	public Set<Desc> getStopAnchor() { return stopAnchor; }
	public Map<String, List<String>> getSelfSupers() { return selfSupers; }
	public final List<State> getSeperatedLibraries() { return extraStates; }
	public ParamNameMapper getParamTypeMapper() { return PARAM_TYPE_MAPPER; }

	/**
	 * By slot
	 * Caution: unmapped class name + mapped method name / descriptor
	 */
	public final void setParamMap(Map<Desc, List<String>> paramMap) {this.paramMap = paramMap;}

	public final State snapshot() { return snapshot(null); }
	public final State snapshot(State s) {
		if (s == null) s = new State();

		if (selfSupers == null) throw new IllegalStateException("internal state is not MAP_FINISHED");

		s.parents.clear();
		s.parents.putAll(selfSupers);
		s.stopAnchor.clear();
		s.stopAnchor.addAll(stopAnchor);
		s.stopAnchor.removeAll(libStopAnchor);
		s.inheritor.clear();
		s.inheritor.putAll(selfInherited);

		return s;
	}

	public static final class State {
		final MyHashMap<String, List<String>> parents = new MyHashMap<>();
		final MyHashSet<Desc> stopAnchor = new MyHashSet<>(Hasher.identity());
		final MyHashMap<Desc, String> inheritor = new MyHashMap<>();
	}

	public void debugRelative(String owner, String name) {
		LOGGER.log(Level.FATAL, "=== relative information for {} ===", null, owner);
		LOGGER.log(Level.FATAL, "==== class map ====", null);
		LOGGER.log(Level.FATAL, "  {} => {}", null, owner, classMap.get(owner));
		List<String> parents = selfSupers.getOrDefault(owner, Collections.emptyList());
		for (int i = 0; i < parents.size(); i++) {
			LOGGER.log(Level.FATAL, "  {} => {}", null, parents.get(i), classMap.get(parents.get(i)));
		}
		LOGGER.log(Level.FATAL, "==== node map ====", null);
		LOGGER.log(Level.FATAL, "F: final; D: direct owner; I: inherited; S: stop anchor (but still listed)", null);
		for (Map.Entry<Desc, String> entry : methodMap.entrySet()) {
			Desc d = entry.getKey();
			if (name != null && !d.name.equals(name)) continue;

			String type;
			String o = d.owner;
			if (o.equals(owner)) type = "D";
			else {
				int i = parents.indexOf(o);
				if (i < 0) continue;
				type = "I";

				d = d.copy();
				while (i-- > 0) {
					d.owner = parents.get(i);
					if (stopAnchor.contains(d)) type = "S";
				}
			}
			if (stopAnchor.contains(d)) type = "F";

			LOGGER.log(Level.FATAL, "  [{}] {} => {}", null, type, entry.getKey(), entry.getValue());
		}
	}
}