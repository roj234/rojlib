package roj.net.http.server.auto;

import org.jetbrains.annotations.Nullable;
import roj.ReferenceByGeneratedClass;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValArray;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.type.*;
import roj.asm.util.Attributes;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.concurrent.task.ITask;
import roj.config.CCJson;
import roj.config.auto.SerializerFactory;
import roj.io.FastFailException;
import roj.net.http.Headers;
import roj.net.http.HttpUtil;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.*;
import roj.reflect.Bypass;
import roj.reflect.ClassDefiner;
import roj.reflect.ReflectionUtils;
import roj.util.AttributeKey;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.asm.Opcodes.*;

/**
 * @author solo6975
 * @since 2022/3/27 14:26
 */
public final class OKRouter implements Router {
	private static final String REQ = "roj/net/http/server/Request";

	private final AttributeKey<Route> RouterImplKey = new AttributeKey<>("or:router");
	private final MyHashMap<String, Dispatcher> interceptors = new MyHashMap<>();

	private final Node route = new Text("");

	private final boolean debug;
	private List<ITask> onFinishes = Collections.emptyList();

	private OKRouter parent;

	public OKRouter() {this(true);}
	public OKRouter(boolean debug) {this.debug = debug;}
	/**
	 * 继承请求拦截器
	 */
	public OKRouter(OKRouter parent) {
		this(parent.debug);
		this.parent = parent;
		this.onFinishes = null;
	}

	/**
	 * 警告：如果使用addPrefixDelegation添加OKRouter，那么onFinish可能不会被触发
	 */
	public void onFinish(ITask callback) {
		if (onFinishes.isEmpty()) onFinishes = new SimpleList<>();
		onFinishes.add(callback);
	}

	public final OKRouter register(Object o) {return register(o, "");}
	public final OKRouter register(Object o, String pathRel) {
		var data = Parser.parseConstants(o.getClass());
		if (data == null) throw new IllegalStateException("找不到"+o.getClass().getName()+"的类文件");

		var hndInst = new ConstantData();
		hndInst.name("roj/net/http/server/auto/Router$"+ReflectionUtils.uniqueId());
		hndInst.interfaces().add("roj/net/http/server/auto/OKRouter$Dispatcher");
		hndInst.parent(Bypass.MAGIC_ACCESSOR_CLASS);
		ClassDefiner.premake(hndInst);

		hndInst.newField(0, "$methodId", "I");
		hndInst.newField(0, "$handler", TypeHelper.class2asm(o.getClass()));

		var cw = hndInst.newMethod(ACC_PUBLIC, "copyWith", "(ILjava/lang/Object;)Lroj/net/http/server/auto/OKRouter$Dispatcher;");
		cw.visitSize(2, 3);

		cw.newObject(hndInst.name);
		cw.one(ASTORE_0);

		cw.one(ALOAD_0);
		cw.one(ILOAD_1);
		cw.field(PUTFIELD, hndInst, 0);

		cw.one(ALOAD_0);
		cw.one(ALOAD_2);
		cw.clazz(CHECKCAST, o.getClass().getName().replace('.', '/'));
		cw.field(PUTFIELD, hndInst, 1);

		cw.one(ALOAD_0);
		cw.one(ARETURN);
		cw.finish();

		cw = hndInst.newMethod(ACC_PUBLIC, "invoke", "(L"+REQ+";Lroj/net/http/server/ResponseHeader;Ljava/lang/Object;)Ljava/lang/Object;");
		cw.visitSize(5, 4);

		cw.one(ALOAD_0);
		cw.field(GETFIELD, hndInst, 1);

		cw.one(ALOAD_0);
		cw.field(GETFIELD, hndInst, 0);

		var seg = CodeWriter.newSwitch(TABLESWITCH);
		cw.addSegment(seg);

		int id = 0;
		IntMap<Annotation> handlers = new IntMap<>();
		ToIntMap<String> interceptorId = new ToIntMap<>();

		List<TryCatchEntry> exhandlers = new SimpleList<>();
		var predefInterceptor = getPredefInterceptor(data);

		var methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			var mn = methods.get(i);

			var list = Attributes.getAnnotations(data.cp, mn, false);
			if (list == null) continue;
			var a = parseAnnotations(list);
			if (a == null) continue;

			if (a.type().equals("roj/net/http/server/auto/Interceptor")) {
				var value = a.getArray("value");
				if (value.size() > 1) throw new IllegalArgumentException("Interceptor的values长度只能为0或1");

				var name = value.isEmpty() ? mn.name() : value.get(0).asString();
				if (this.interceptors.containsKey(name)) throw new IllegalStateException("拦截器名称"+name+"在当前OKRouter重复");
				interceptorId.putInt(name, id++);
			} else {
				if (predefInterceptor != null) {
					var self = a.getArray("interceptor");
					if (self.isEmpty()) a.put("interceptor", new AnnValArray(predefInterceptor));
					else self.addAll(predefInterceptor);
				}

				handlers.putInt(id++, a);
				if (a.getString("value") == null)
					a.put("value", AnnVal.valueOf(mn.name().replace("__", "/")));
			}

			Label self = cw.label();
			seg.branch(seg.targets.size(), self);
			seg.def = self;
			List<Type> par = mn.parameters();

			noBody:{
				int begin = 2;

				hasBody: {
					if (par.isEmpty()) break noBody;

					if (!REQ.equals(par.get(0).owner)) {
						begin = 0;
						break hasBody;
					}

					if (par.size() == 1) {
						cw.one(ALOAD_1);
						break noBody;
					}

					if(!"roj/net/http/server/ResponseHeader".equals(par.get(1).owner)) {
						cw.one(ALOAD_1);
						begin = 1;
						break hasBody;
					}

					cw.one(ALOAD_1);
					cw.one(ALOAD_2);
					if (par.size() <= 2) break noBody;
				}

				if (a.type().equals("roj/net/http/server/auto/Interceptor")) {
					cw.one(ALOAD_3);
					cw.clazz(CHECKCAST, PostSetting.class.getName().replace('.', '/'));
				} else {
					provideBodyPars(cw, data.cp, mn, begin, exhandlers);
				}
			}

			cw.invoke(INVOKEVIRTUAL, mn.ownerClass(), mn.name(), mn.rawDesc());
			if (mn.returnType().type != Type.CLASS) {
				if (mn.returnType().type != Type.VOID)
					throw new IllegalArgumentException("方法返回值不是void或对象:"+mn);
				else cw.one(ACONST_NULL);
			}
			cw.one(ARETURN);
		}
		if (seg.def == null) throw new IllegalArgumentException(data.name+"没有任何处理函数");

		if (debug) {
			for (var tce : exhandlers) {
				cw.label(tce.handler);
				cw.ldc("参数'"+tce.type+"'解析失败");
				cw.invoke(INVOKESTATIC, "roj/net/http/server/auto/OKRouter", "requestDebug", "(Ljava/lang/Throwable;Ljava/lang/String;)Lroj/net/http/IllegalRequestException;");
				cw.one(ATHROW);
			}
		} else {
			for (var tce : exhandlers) {
				cw.label(tce.handler);
				cw.field(GETSTATIC, "roj/net/http/IllegalRequestException", "BAD_REQUEST", "Lroj/net/http/IllegalRequestException;");
				cw.one(ATHROW);
			}
		}
		cw.visitExceptions();
		for (var tce : exhandlers) cw.visitException(tce.start,tce.end,tce.handler,null);
		cw.finish();

		var ah = (Dispatcher) ClassDefiner.make(hndInst, o.getClass().getClassLoader());
		for (var entry : handlers.selfEntrySet()) {
			int i = entry.getIntKey();
			var a = entry.getValue();
			var set = new Route();

			set.accepts = a.getInt("accepts", 255);
			set.req = ah.copyWith(i, o);
			set.mime = a.getString("mime");

			List<Dispatcher> precs = Collections.emptyList();
			List<AnnVal> vname = a.getArray("interceptor");
			if (!vname.isEmpty()) {
				for (int j = 0; j < vname.size(); j++) {
					String name = vname.get(j).asString();
					var prec = interceptors.get(name);
					if (prec == null) {
						// dummy interceptor
						if (interceptors.containsKey(name)) continue;

						int vid = interceptorId.getOrDefault(name, -1);
						if (vid < 0) {
							if ((prec = getParentInterceptor(name)) == null) {
								throw new IllegalArgumentException("未找到"+a+"引用的拦截器"+vname.get(j));
							}
						} else {
							prec = ah.copyWith(vid, o);
							interceptors.put(name, prec);
						}
					}

					if (precs.size() == 0) {
						precs = Collections.singletonList(prec);
					} else {
						if (precs.size() == 1) precs = new SimpleList<>(precs);
						precs.add(prec);
					}
				}
			}
			set.prec = precs.toArray(new Dispatcher[precs.size()]);

			int flag = a.getBoolean("prefix")?Node.PREFIX:0;
			String url = pathRel.concat(a.getString("value"));
			if (url.endsWith("/")) flag |= Node.DIRECTORY;

			Node node = route.add(url, 0, url.length());

			Object prev = node.value;
			if (prev == null) {
				node.flag |= (byte) flag;
				node.value = set;
			} else {
				if (node.flag != flag) throw new IllegalArgumentException("prefix定义不同/"+o+" in "+prev+"|"+set);

				if (prev instanceof Route prevReq) {
					Route[] newReq = new Route[8];
					node.value = newReq;

					if ((prevReq.accepts & set.accepts) != 0)
						throw new IllegalArgumentException("冲突的请求类型处理器/"+o+" in "+prevReq+"|"+set);

					for (int j = 0; j < 8; j++) {
						if ((set.accepts & (1<<j)) != 0) {
							newReq[j] = set;
						} else if ((prevReq.accepts & (1<<j)) != 0) {
							newReq[j] = prevReq;
						}
					}
				} else {
					Route[] newReq = ((Route[]) prev);
					for (int j = 0; j < 8; j++) {
						if ((set.accepts & (1<<j)) != 0) {
							if (newReq[j] != null)
								throw new IllegalArgumentException("冲突的请求类型处理器/"+o+" in "+newReq[j]+"|"+set+":"+HttpUtil.getMethodName(j));
							newReq[j] = set;
						}
					}
				}
			}
		}

		for (var entry : interceptorId.selfEntrySet()) {
			if (!interceptors.containsKey(entry.k)) {
				interceptors.put(entry.k, ah.copyWith(entry.v, o));
			}
		}
		return this;
	}
	private Dispatcher getParentInterceptor(String name) {
		var p = parent;
		while (p != null) {
			var i = p.interceptors.get(name);
			if (i != null) return i;
			p = p.parent;
		}
		return null;
	}

	@Nullable
	private static List<AnnVal> getPredefInterceptor(ConstantData data) {
		var preDef = Attributes.getAnnotation(Attributes.getAnnotations(data.cp, data, false), "roj/net/http/server/auto/Interceptor");
		return preDef != null ? preDef.getArray("value") : null;
	}
	private static Annotation parseAnnotations(List<Annotation> list) {
		AnnVal accepts = null, mime = null;
		Annotation body = null, interceptor = null, route = null, easyMapping = null;

		for (int j = 0; j < list.size(); j++) {
			var a = list.get(j);
			switch (a.type()) {
				case "roj/net/http/server/auto/Interceptor" -> interceptor = a;
				case "roj/net/http/server/auto/Route" -> route = a;
				case "roj/net/http/server/auto/Accepts" -> accepts = a.values.get("value");
				case "roj/net/http/server/auto/Body" -> body = a;
				case "roj/net/http/server/auto/Mime" -> mime = a.values.get("value");
				case "roj/net/http/server/auto/GET", "roj/net/http/server/auto/POST" -> {
					if (easyMapping != null) throw new IllegalArgumentException(easyMapping+"不能和"+a+"共用");
					easyMapping = a;
				}
			}
		}

		if (easyMapping != null) {
			if (route != null) throw new IllegalArgumentException(easyMapping+"不能和@Route共用");
			if (accepts != null) throw new IllegalArgumentException(easyMapping+"不能和@Accepts共用");

			boolean isGet = easyMapping.type().endsWith("GET");
			if (body == null) list.add(new Annotation("roj/net/http/server/auto/Body", Collections.singletonMap("value", new AnnValEnum("roj/net/http/server/auto/From", isGet ? "GET" : "POST_KV"))));

			var map = easyMapping.values;
			if (map.isEmpty()) map = easyMapping.values = new MyHashMap<>();
			map.put("accepts", AnnVal.valueOf(isGet ? Accepts.GET : Accepts.POST));

			route = easyMapping;
			route.setType("roj/net/http/server/auto/Route");
		} else if (route == null) return interceptor;

		var values = route.values;
		if (values.isEmpty()) values = route.values = new MyHashMap<>();
		if (interceptor != null) values.put("interceptor", interceptor.values.get("value"));
		if (mime != null) values.put("mime", mime);

		return route;
	}

	public final Dispatcher getInterceptor(String name) {return interceptors.get(name);}
	public final void setInterceptor(String name, Dispatcher interceptor) {interceptors.put(name, interceptor);}
	public final void removeInterceptor(String name) {interceptors.remove(name);}

	public final OKRouter addPrefixDelegation(String path, Router router) {return addPrefixDelegation(path, router, (String[])null);}
	public final OKRouter addPrefixDelegation(String path, Router router, @Nullable String... interceptors) {
		Node node = route.add(path, 0, path.length());
		if (node.value != null) throw new IllegalArgumentException("子路径"+path+"已存在");

		Route aset = new Route();

		node.flag |= (byte) (Node.PREFIX|(path.endsWith("/")?Node.DIRECTORY:0));
		node.value = aset;
		aset.accepts = 511;

		Dispatcher lastCheck = (req, srv, extra) -> {
			router.checkHeader(req, (PostSetting) extra);
			return null;
		};
		if (interceptors == null || interceptors.length == 0) aset.prec = new Dispatcher[] {lastCheck};
		else {
			var prec = new Dispatcher[interceptors.length + 1];
			int size = 0;
			for (String name : interceptors) {
				Dispatcher dp = this.interceptors.get(name);
				if (dp == null) {
					if (!this.interceptors.containsKey(name))
						throw new IllegalArgumentException("无法找到请求拦截器"+name);
				} else {
					prec[size++] = dp;
				}
			}
			prec[size] = lastCheck;
			aset.prec = size == interceptors.length ? prec : Arrays.copyOf(prec, size+1);
		}
		aset.req = (req, srv, extra) -> {
			try {
				return router.response(req, srv);
			} catch (Exception e) {
				Helpers.athrow(e);
			}
			return null;
		};

		return this;
	}
	public final boolean removePrefixDelegation(String path) {return route.remove(path, 0, path.length());}

	private void provideBodyPars(CodeWriter c, ConstantPool cp, MethodNode m, int begin, List<TryCatchEntry> tries) {
		List<Type> parTypes = m.parameters();

		Annotation body = Attributes.getAnnotation(Attributes.getAnnotations(cp, m, false), "roj/net/http/server/auto/Body");
		if (body == null) throw new IllegalArgumentException("没有@Body注解 " + m);

		List<List<Annotation>> annos = Attributes.getParameterAnnotation(cp, m, false);
		if (annos == null) annos = Collections.emptyList();

		Signature signature = m.parsedAttr(cp, Attribute.SIGNATURE);
		List<IType> genTypes = signature == null ? Collections.emptyList() : signature.values;

		List<String> parNames = ParamNameMapper.getParameterName(cp, m);
		if (parNames == null) parNames = Collections.emptyList();

		BodyPre bw = new BodyPre();
		bw.cw = c;

		String from = body.getEnumValue("value", "INHERIT");
		if (from.equals("INHERIT")) throw new IllegalArgumentException(body + "不能使用INHERIT");
		bw.from = from;
		bw.nonnull = body.getBoolean("nonnull", false);
		bw.tries = tries;

		for (; begin < parTypes.size(); begin++) {
			bw.process(begin>=annos.size()?null: Attributes.getAnnotation(annos.get(begin), "roj/net/http/server/auto/Field"),
				begin>=parNames.size()?null:parNames.get(begin),
				parTypes.get(begin),
				begin>=genTypes.size()|| !(genTypes.get(begin) instanceof Generic) ? null: (Generic) genTypes.get(begin));
		}

		c.visitSizeMax(TypeHelper.paramSize(m.rawDesc())+2, bw.nextSlot);
	}
	private static final class BodyPre {
		// slot0 this, slot1 request, slot2 handler
		int slot, nextSlot = 3;
		// instance, req, rh
		int bodyKind;
		CodeWriter cw;
		String from;
		boolean nonnull;
		List<TryCatchEntry> tries;

		private static final Annotation DEFAULT = new Annotation();

		void process(@Nullable Annotation field, String name, Type type, Generic genType) {
			if (field == null) field = DEFAULT;

			String from1 = field.getEnumValue("from", "INHERIT");
			if (from1.equals("INHERIT")) from1 = from;
			int fromSlot = 0;

			if (name == null && (name = field.getString("value")) == null && !from1.equals("JSON"))
				throw new IllegalArgumentException("编译时是否保存了方法参数名称？");

			CodeWriter c = cw;
			switch (from1) {
				case "POST_KV":
					fromSlot = (slot >>> 8) & 0xFF;
					if (fromSlot == 0) {
						bodyKind |= 2;

						c.one(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "PostFields", "()Ljava/util/Map;");
						c.vars(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++ << 8;
					}
				break;
				case "GET":
					fromSlot = (slot) & 0xFF;
					if (fromSlot == 0) {
						bodyKind |= 1;

						c.one(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "GetFields", "()Ljava/util/Map;");
						c.vars(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++;
					}
				break;
				case "REQUEST":
					fromSlot = (slot >>> 16) & 0xFF;
					if (fromSlot == 0) {
						bodyKind |= 3;

						c.one(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "fields", "()Ljava/util/Map;");
						c.vars(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++ << 16;
					}
				break;
				case "JSON":
					if ((bodyKind & 4) == 0) {
						bodyKind |= 4;

						if (type.getActualType() != Type.CLASS) throw new IllegalArgumentException("基本类型无法使用JSON解析");

						var tce = new TryCatchEntry();
						tce.start = cw.label();
						tce.handler = new Label();
						tce.type = type+" "+name;

						c.one(ALOAD_1);
						c.ldc(new CstString(genType==null?type.toDesc():genType.toDesc()));
						c.invoke(INVOKESTATIC, "roj/net/http/server/auto/OKRouter", "parse", "(L"+REQ+";Ljava/lang/String;)Ljava/lang/Object;");
						c.clazz(CHECKCAST, type);

						tce.end = cw.label();
						tries.add(tce);
						return;
					} else {
						throw new IllegalArgumentException("JSON类型仅能出现一次(这是反序列化请求体啊)");
					}
			}
			if ((bodyKind & 6) == 6) throw new IllegalArgumentException("不能同时使用POST_KV和JSON");

			if (fromSlot == 0) throw new IllegalStateException("不支持的类型/"+from1);

			var tce = new TryCatchEntry();
			tce.start = cw.label();
			tce.handler = new Label();
			tce.type = type+" "+name;

			c.vars(ALOAD, fromSlot);
			c.ldc(name);
			String orDefault = field.getString("orDefault", null);
			if (orDefault != null) {
				c.ldc(orDefault);
				c.invokeItf("java/util/Map", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
			} else {
				c.invokeItf("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
				cw.one(DUP);
				var label = new Label();
				cw.jump(IFNONNULL, label);
				cw.one(POP);
				cw.clazz(NEW, "roj/io/FastFailException");
				cw.one(DUP);
				cw.ldc("该必选参数不存在");
				cw.invokeD("roj/io/FastFailException", "<init>", "(Ljava/lang/String;)V");
				cw.one(ATHROW);
				cw.label(label);
			}

			addExHandler:{
			if (type.owner != null || type.array() > 0) {
				if (type.owner != null) {
					if (type.owner.equals("java/lang/String") ||
						type.owner.equals("java/lang/CharSequence")) {
						cw.invoke(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
						break addExHandler;
					}
				}
				throw new IllegalArgumentException("不知道如何将String转换为"+type);
			}
			if (type.type == Type.CHAR) {
				// stack=4;
				cw.invoke(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I");
				cw.one(DUP);
				cw.one(DUP);
				cw.one(I2C);
				Label label = new Label();
				cw.jump(IF_icmpeq, label);
				cw.clazz(NEW, "roj/io/FastFailException");
				cw.one(DUP);
				cw.ldc("值不能为零");
				cw.invokeD("roj/io/FastFailException", "<init>", "(Ljava/lang/String;)V");
				cw.one(ATHROW);
				cw.label(label);
			} else {
				name = Type.toString(type.type);
				name = Character.toUpperCase(name.charAt(0))+name.substring(1);
				cw.clazz(CHECKCAST, "java/lang/String");
				cw.invoke(INVOKESTATIC, "java/lang/"+(name.equals("Int")?"Integer":name), "parse"+name, "(Ljava/lang/String;)"+(char)type.type);
			}
			}
			tce.end = cw.label();
			tries.add(tce);
		}
	}

	private static final class Route {
		int accepts;
		String mime;
		Dispatcher req;
		Dispatcher[] prec;
	}
	private static Route getASet(Object o, int action) { return o instanceof Route ? ((Route) o) : ((Route[]) o)[action]; }

	private static final class RouteMatcher {
		private final SimpleList<Node> env1 = new SimpleList<>(), env2 = new SimpleList<>();
		private final SimpleList<SimpleList<String>> par3 = new SimpleList<>(), par4 = new SimpleList<>();

		int prefixLen;
		Object value;
		Headers params;

		private Node prefixNode;
		private SimpleList<String> prefixPar;
		private int prefixParSize;

		final boolean match(Node root, String path, int i, int end) {
			var nodeS = env1;
			var nodeD = env2;
			var parS = par3;
			var parD = par4;

			nodeS.add(root);

			prefixNode = null;
			prefixPar = null;

			int prevI = 0;
			while (i < end) {
				int nextI = path.indexOf('/', i);
				if (nextI >= end || nextI < 0) nextI = end;

				for (int k = 0; k < nodeS.size(); k++) {
					Node node = nodeS.get(k);

					if ((node.flag & Node.PREFIX) != 0) {
						prefixLen = i;
						prefixNode = node;
						prefixPar = parS.size() <= k ? null : parS.get(k);
						if (prefixPar != null) prefixParSize = prefixPar.size();
					}

					int pos = nodeD.size();
					String parName = node.get(path, i, nextI, nodeD);

					syncParam:
					if (nodeD.size() > pos) {
						SimpleList<String> params = parS.size() <= k ? null : parS.get(k);
						if (parName != null) {
							if (params == null) params = new SimpleList<>();
							params.add(parName);
							params.add(path.substring(prevI, i-1));
						} else if (params == null) break syncParam;

						parD.ensureCapacity(nodeD.size());
						parD._setSize(nodeD.size());

						Object[] array = parD.getInternalArray();
						for (int l = pos; l < nodeD.size(); l++) {
							array[l] = params;
						}
					}
				}

				if (nodeD.isEmpty()) {
					parS.clear();
					nodeS.clear();
					return buildPrefix();
				}

				var tmp = nodeS;
				nodeS = nodeD;
				nodeD = tmp;
				nodeD.clear();

				var tmp2 = parS;
				parS = parD;
				parD = tmp2;
				parD.clear();

				prevI = i;
				i = nextI+1;
			}

			Node node = null;
			int priority = -1;
			SimpleList<String> par = null;

			boolean noDir = path.charAt(end - 1) != '/';
			for (int j = 0; j < nodeS.size(); j++) {
				Node n = nodeS.get(j);
				if (n.value != null) {
					// prefix只接受目录
					if ((n.flag&Node.DIRECTORY) != 0 && noDir) {
						continue;
					}

					int prio = n.priority();
					if (prio > priority) {
						node = n;
						par = parS.size() <= j ? null : parS.get(j);

						if (node instanceof Regex rex) {
							if (par == null) par = new SimpleList<>();

							par.add(rex.name);
							par.add(path.substring(prevI, noDir ? end : end-1));
						}
					} else if (prio == priority && node != null) { // equals
						System.out.println("same: " + node);
						System.out.println("same: " + n);
					}
				}
			}

			parS.clear();
			nodeS.clear();

			if (node == null) return buildPrefix();

			prefixLen = end;
			value = node.value instanceof Node n ? n.value : node.value;
			if (par != null) {
				params = new Headers();
				int j = 0;
				while (j < par.size()) {
					params.add(par.get(j++), par.get(j++));
				}
			} else {
				params = null;
			}
			return true;
		}

		private boolean buildPrefix() {
			if (prefixNode == null) return false;

			value = prefixNode.value;
			SimpleList<String> par = prefixPar;
			if (par != null) {
				params = new Headers();
				int i = 0;
				while (i < prefixParSize) {
					params.add(par.get(i++), par.get(i++));
				}
			} else {
				params = null;
			}

			prefixNode = null;
			prefixPar = null;
			return true;
		}
	}
	private static abstract class Node {
		String name;
		// Route or Route[]
		Object value;

		byte flag;
		static final byte PREFIX = 1, DIRECTORY = 2;

		private int mask;
		private Text[] table;
		int size;

		private List<Regex> any = Collections.emptyList();

		public Node(String name) { this.name = name; }

		public boolean remove(String path, int i, int end) {
			if (i >= end) return true;

			int j = path.indexOf('/', i);
			if (j >= end || j < 0) j = end;

			if (path.charAt(i) == ':') {
				throw new IllegalArgumentException("not supported removing regexp route, try prefix delegation");
			}

			if (table != null) {
				int hash = hash(path, i, j);

				Text prev = null;
				Text node = table[hash & mask];
				while (node != null) {
					if (path.regionMatches(i, node.name, 0, node.name.length())) {
						if (!node.remove(path, j + 1, end)) return false;

						if (node.size == 0) {
							if (prev != null) prev.next = node.next;
							else table[hash & mask] = node.next;
						}

						if (--size == 0) table = null;
						return true;
					}

					prev = node;
					node = node.next;
				}
			}

			return false;
		}

		Node add(String path, int i, int end) {
			int j = path.indexOf('/', i);
			if (j >= end || j < 0) j = end;
			if (i >= j) return this;

			if (table == null) {
				table = new Text[4];
				mask = 3;
			}

			int hash = hash(path, i, j);
			int count = 0;
			Text node = table[hash & mask];
			while (node != null) {
				if (path.regionMatches(i, node.name, 0, node.name.length())) {
					return node.add(path, j+1, end);
				}
				node = node.next;
				count++;
			}

			if (count >= 3) resize();

			Node node1;
			if (path.charAt(i) == ':') {
				// regexNode
				var node2 = new Regex(path.substring(i+1, j));
				if (any.isEmpty()) any = new SimpleList<>();

				i = any.indexOf(node2);
				if (i >= 0) node1 = any.get(i);
				else {
					if ((node2.flag&2) != 0 && j >= end) {
						if (value != null) throw new IllegalStateException();
						value = node2;
					}
					any.add(node2);
					node1 = node2;
				}
			} else {
				node = new Text(path.substring(i, j));
				node.next = table[hash &= mask];
				table[hash] = node;
				size++;

				node1 = node;
			}
			return node1.add(path, j+1, end);
		}
		static int hash(String s, int off, int end) {
			int h = 1;
			while (off < end) h = 31*h + s.charAt(off++);
			return h;
		}
		private void resize() {
			int length = (mask+1) << 1;
			Text[] tab1 = new Text[length];
			int mask1 = length-1;

			int i = 0, j = table.length;
			for (; i < j; i++) {
				Text entry = table[i];

				while (entry != null) {
					Text next = entry.next;

					int newKey = hash(entry.name, 0, entry.name.length()) & mask1;

					entry.next = tab1[newKey];
					tab1[newKey] = entry;

					entry = next;
				}
			}

			this.table = tab1;
			this.mask = mask1;
		}

		String get(String path, int i, int end, List<Node> nodes) {
			if (table != null) {
				int hash = hash(path, i, end);
				Text node = table[hash & mask];
				while (node != null) {
					if (path.regionMatches(i, node.name, 0, node.name.length())) {
						nodes.add(node);
					}
					node = node.next;
				}
			}

			for (int j = 0; j < any.size(); j++) {
				any.get(j).match(path, i, end, nodes);
			}

			return null;
		}

		int priority() {
			// 高于正则匹配
			int prio = 5;
			// 完整匹配 > 前缀匹配
			if ((flag & 1) == 0) prio++;
			return prio;
		}
	}
	private static final class Text extends Node {
		Text next;
		Text(String name) { super(name); }
	}
	private static final class Regex extends Node {
		Pattern regexp;

		static final Pattern REGEXP = Pattern.compile("([A-Za-z]+)(\\(.+?\\))?([+*?])?");
		Regex(String url) {
			super("regexp");

			Matcher m = REGEXP.matcher(url);
			if (!m.matches()) throw new IllegalArgumentException(url);

			name = m.group(1);
			String regexp = m.group(2);
			if (regexp != null) this.regexp = Pattern.compile(regexp);
			String count = m.group(3);
			if (count != null) {
				if (count.equals("+")) {
					flag |= 4; // MORE
				} else if (count.equals("*")) {
					flag |= 12; // ZERO | MORE
				} else {
					flag |= 8; // ZERO
				}
			}
		}

		final void match(String path, int i, int end, List<Node> nodes) {
			if (regexp != null) {
				Matcher m = regexp.matcher(path);
				if (!m.find(i) || m.start() != i || m.end() != end) {
					if ((flag&8) != 0) super.get(path, i, end, nodes);
					return;
				}
			}

			nodes.add(this);
		}

		@Override
		final String get(String path, int i, int end, List<Node> nodes) {
			if ((flag&4) != 0) {
				Matcher m;
				if (regexp == null || (m = regexp.matcher(path)).find(i) && m.start() == i && m.end() == end) {
					nodes.add(this);
				}
			}

			super.get(path, i, end, nodes);
			return name;
		}

		@Override
		int priority() {
			int prio = 0;
			// 有限匹配 > 无限匹配
			if ((flag & 12) == 0) prio++;
			// 必选匹配 > 可选匹配
			if ((flag & 4) == 0) prio++;
			// 有限制 > 无限制
			if (regexp != null) prio++;
			return prio;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			Regex regex = (Regex) o;
			if (!name.equals(regex.name) || flag != regex.flag) return false;
			return regexp != null ? regexp.pattern().equals(regex.regexp.pattern()) : regex.regexp == null;
		}

		@Override
		public int hashCode() {
			int h = (name.hashCode() << 1) ^ flag;
			return regexp != null ? h^regexp.hashCode() : h;
		}
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		String path = req.path();
		int len = path.length();

		Route set;
		if (len == 0) {
			Object o = route.value;
			set = o == null ? null : getASet(o, req.action());
		} else {
			var m = (RouteMatcher) req.localCtx().get("or:fn");
			if (m == null) req.localCtx().put("or:fn", m = new RouteMatcher());

			if (m.match(route, path, 0, len) && m.value != null) {
				set = getASet(m.value, req.action());
				req.setPath(path.substring(m.prefixLen));
				req.setArguments(m.params);
			} else {
				set = null;
			}
		}

		if (set == null) throw new IllegalRequestException(403);
		if (((1 << req.action()) & set.accepts) == 0) throw new IllegalRequestException(HttpUtil.METHOD_NOT_ALLOWED);

		req.connection().attachment(RouterImplKey, set);
		for (Dispatcher prec : set.prec) {
			Object ret = prec.invoke(req, req.server(), cfg);
			if (ret != null) {
				if (ret instanceof Response r) throw new IllegalRequestException(200, r);
				throw new IllegalRequestException(200, ret.toString());
			}
		}

		if (cfg != null && !cfg.postAccepted()) Router.super.checkHeader(req, cfg);
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		Route set = req.connection().attachment(RouterImplKey, null);

		Object ret;
		try {
			ret = set.req.invoke(req, rh, req.arguments());
		} finally {
			for (var c : onFinishes) {
				try {
					c.execute();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (ret instanceof Response r) return r;
		if (ret == null) return null;
		return new StringResponse(ret.toString(), set.mime != null ? set.mime : "text/plain");
	}

	@ReferenceByGeneratedClass
	public static IllegalRequestException requestDebug(Throwable exc, String msg) {return new IllegalRequestException(400, Response.internalError(msg, exc));}
	@ReferenceByGeneratedClass
	public static Object parse(Request req, String type) throws Exception {
		if (req.postBuffer() == null) throw new FastFailException("没有请求体");

		var parser = (CCJson) req.localCtx().get("or:parser");
		if (parser == null) req.localCtx().put("or:parser", parser = new CCJson());

		var adapter = SerializerFactory.SAFE.serializer(Signature.parseGeneric(type));
		adapter.reset();
		parser.parse(req.postBuffer(), 0, adapter);
		return adapter.get();
	}

	public interface Dispatcher {
		Object invoke(Request req, ResponseHeader srv, Object extra) throws IllegalRequestException;
		default Dispatcher copyWith(int methodId, Object ref) { return this; }
	}
}