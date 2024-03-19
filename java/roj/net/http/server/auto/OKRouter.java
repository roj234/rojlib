package roj.net.http.server.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.TryCatchEntry;
import roj.asm.type.*;
import roj.asm.util.Attributes;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.SwitchSegment;
import roj.asmx.mapper.ParamNameMapper;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.collect.TrieTree;
import roj.config.CCJson;
import roj.config.auto.Serializer;
import roj.config.auto.Serializers;
import roj.math.MutableInt;
import roj.net.http.Action;
import roj.net.http.HttpCode;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.*;
import roj.reflect.FastInit;
import roj.reflect.ReflectionUtils;
import roj.util.AttributeKey;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.asm.Opcodes.*;

/**
 * @author solo6975
 * @since 2022/3/27 14:26
 */
public class OKRouter implements Router {
	private static final int PREFIX = 256, REGEXP = 512;
	private static final String REQ = Request.class.getName().replace('.', '/');
	private static final String RESP = ResponseHeader.class.getName().replace('.', '/');

	private static final String SELF = "roj/net/http/srv/auto/OKRouter";
	private static final String INVOKE_DESC = TypeHelper.class2asm(new Class<?>[] {Request.class, ResponseHeader.class, Object.class}, Object.class);
	private static final String COPYWITH_DESC = TypeHelper.class2asm(new Class<?>[] {int.class, Object.class}, Dispatcher.class);

	private static final AttributeKey<RouterResult> RouterArgumentKey = new AttributeKey<>("or:args");
	private static final AttributeKey<Route> RouteAdapterKey = new AttributeKey<>("or:router");

	// ASet or ASet[]
	private final TrieTree<Object> route = new TrieTree<>();
	private boolean handleError = true;

	private List<Callable<Void>> prCallback = Collections.emptyList();

	public OKRouter() {}

	public void setHandleError(boolean b) { handleError = b; }
	public void afterRequest(Callable<Void> callback) {
		if (prCallback.isEmpty()) prCallback = new SimpleList<>();
		prCallback.add(callback);
	}

	public final OKRouter register(Object o) {
		ConstantData hndInst = new ConstantData();
		hndInst.name("roj/net/http/srv/auto/$Route$" +ReflectionUtils.uniqueId());
		hndInst.interfaces().add("roj/net/http/srv/auto/OKRouter$Dispatcher");
		FastInit.prepare(hndInst);

		hndInst.newField(0, "$methodId", "I");
		hndInst.newField(0, "$handler", TypeHelper.class2asm(o.getClass()));

		CodeWriter cw = hndInst.newMethod(ACC_PUBLIC, "copyWith", COPYWITH_DESC);
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

		cw = hndInst.newMethod(ACC_PUBLIC, "invoke", INVOKE_DESC);
		cw.visitSize(5, 4);

		cw.one(ALOAD_0);
		cw.field(GETFIELD, hndInst, 1);

		cw.one(ALOAD_0);
		cw.field(GETFIELD, hndInst, 0);

		SwitchSegment seg = CodeWriter.newSwitch(TABLESWITCH);
		cw.addSegment(seg);

		int id = 0;
		IntMap<Annotation> handlers = new IntMap<>();
		ToIntMap<String> interceptors = new ToIntMap<>();

		String fn = o.getClass().getName().replace('.', '/').concat(".class");
		ConstantData data = Parser.parseConstants(o.getClass());

		List<TryCatchEntry> exhandlers = new SimpleList<>();

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);

			List<Annotation> list = Attributes.getAnnotations(data.cp, mn, false);
			Annotation a = Attributes.getAnnotation(list, "roj/net/http/srv/auto/Route");
			Annotation a2 = Attributes.getAnnotation(list, "roj/net/http/srv/auto/Interceptor");
			if (a == null) {
				if (a2 == null) continue;
				List<AnnVal> value = a2.getArray("value");
				if (value.size() > 1) throw new IllegalArgumentException("Interceptor的values长度只能为0或1");

				interceptors.putInt(value.isEmpty()?mn.name():value.get(0).asString(), id++);
				a = a2;
			} else {
				handlers.putInt(id++, a);
				if (a.getString("value") == null)
					a.put("value", AnnVal.valueOf(mn.name().replace("__", "/")));
				if (a2 != null) a.put("interceptor", a2.values.get("value"));
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

					if(!RESP.equals(par.get(1).owner)) {
						cw.one(ALOAD_1);
						begin = 1;
						break hasBody;
					}

					cw.one(ALOAD_1);
					cw.one(ALOAD_2);
					if (par.size() <= 2) break noBody;
				}

				if (a.type().endsWith("Interceptor")) {
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

			Annotation a1 = Attributes.getAnnotation(list, "roj/net/http/srv/auto/Accepts");
			if (a1 != null) a.put("accepts", a1.values.get("value"));
		}
		if (seg.def == null) throw new IllegalArgumentException(fn.concat("没有任何处理函数"));

		if (handleError) {
			for (TryCatchEntry eh : exhandlers) {
				cw.label(eh.handler);
				cw.one(ASTORE_0);

				cw.ldc("参数'"+eh.type+"'解析失败: ");
				cw.one(ALOAD_0);
				cw.invoke(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
				cw.invoke(INVOKEVIRTUAL, "java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;");

				cw.one(ASTORE_0);

				cw.clazz(NEW, "roj/net/http/IllegalRequestException");
				cw.one(DUP);
				cw.ldc(400);
				cw.one(ALOAD_0);
				cw.invoke(INVOKESPECIAL, "roj/net/http/IllegalRequestException", "<init>", "(ILjava/lang/String;)V");
				cw.one(ATHROW);
			}
			cw.visitExceptions();
			for (TryCatchEntry eh : exhandlers) {
				cw.visitException(eh.start,eh.end,eh.handler,null);
			}
			cw.finish();
		}

		Dispatcher ah = (Dispatcher) FastInit.make(hndInst);
		for (IntMap.Entry<Annotation> entry : handlers.selfEntrySet()) {
			int i = entry.getIntKey();
			Annotation a = entry.getValue();
			Route set = new Route();

			set.accepts = a.getInt("accepts", 255);
			set.req = ah.copyWith(i, o);

			List<Dispatcher> precs = Collections.emptyList();
			List<AnnVal> vname = a.getArray("interceptor");
			if (!vname.isEmpty()) {
				for (int j = 0; j < vname.size(); j++) {
					int vid = interceptors.getOrDefault(vname.get(j).asString(), -1);
					if (vid < 0) throw new IllegalArgumentException("未找到"+a+"引用的拦截器"+vname.get(j));

					Dispatcher prec = ah.copyWith(vid, o);
					if (precs.size() == 0) {
						precs = Collections.singletonList(prec);
					} else {
						if (precs.size() == 1) precs = new SimpleList<>(precs);
						precs.add(prec);
					}
				}
			}
			set.prec = precs.toArray(new Dispatcher[precs.size()]);

			String type = a.getEnumValue("type", "LITERAL");
			String url = a.getString("value");
			switch (type) {
				case "LITERAL" -> {
					if (url.endsWith("/")) {
						url = url.substring(0, url.length() - 1);
					}
				}
				case "PREFIX" -> set.accepts |= PREFIX;
				case "VUE_LIKE" -> {
					set.accepts |= REGEXP;
					RouteMatcher m = new RouteMatcher(url);
					url = m.getPrefix();
					routeMatchers.add(m);
				}
				default -> throw new IllegalStateException("未知的路径匹配类型:" + type);
			}

			Object prev = route.putIfAbsent(url, set);
			if (prev instanceof Route prevReq) {
				Route[] newReq = new Route[8];

				if ((prevReq.accepts & set.accepts) != 0)
					throw new IllegalArgumentException("冲突的请求类型处理器/"+o+" in "+prevReq+"|"+set);

				for (int j = 0; j < 8; j++) {
					if ((set.accepts & (1<<j)) != 0) {
						newReq[j] = set;
					} else if ((prevReq.accepts & (1<<j)) != 0) {
						newReq[j] = prevReq;
					}
				}

				route.put(url, newReq);
			} else if (prev != null) {
				Route[] newReq = ((Route[]) prev);
				for (int j = 0; j < 8; j++) {
					if ((set.accepts & (1<<j)) != 0) {
						if (newReq[j] != null)
							throw new IllegalArgumentException("冲突的请求类型处理器/"+o+" in "+newReq[j]+"|"+set+":"+Action.toString(j));
						newReq[j] = set;
					}
				}
			}
		}

		return this;
	}
	@Deprecated
	public final OKRouter registerSubpathRouter(String path, Router router) {
		Route aset = new Route();
		aset.accepts = 511;
		aset.prec = new Dispatcher[] {
			(req, srv, extra) -> {
				try {
					router.checkHeader(req.subDirectory(path), (PostSetting) extra);
				} catch (Exception e) {
					Helpers.athrow(e);
				} finally {
					req.resetPath();
				}
				return null;
			}
		};
		aset.req = (req, srv, extra) -> {
			try {
				return router.response(req.subDirectory(path), srv);
			} catch (Exception e) {
				Helpers.athrow(e);
			} finally {
				req.resetPath();
			}
			return null;
		};
		route.put(path, aset);
		return this;
	}
	@Deprecated
	public final boolean unregisterSubpathRouter(String path) {
		return route.remove(path) != null;
	}

	private void provideBodyPars(CodeWriter c, ConstantPool cp, MethodNode m, int begin, List<TryCatchEntry> tries) {
		List<Type> parTypes = m.parameters();

		Annotation body = Attributes.getAnnotation(Attributes.getAnnotations(cp, m, false), "roj/net/http/srv/auto/Body");
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
			bw.process(begin>=annos.size()?null: Attributes.getAnnotation(annos.get(begin), "roj/net/http/srv/auto/Field"),
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
						c.invoke(INVOKEVIRTUAL, REQ, "postFields", "()Ljava/util/Map;");
						c.vars(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++ << 8;
					}
				break;
				case "GET":
					fromSlot = (slot) & 0xFF;
					if (fromSlot == 0) {
						bodyKind |= 1;

						c.one(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "GET_Fields", "()Ljava/util/Map;");
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

						if (type.getActualType() != Type.CLASS) throw new IllegalArgumentException("那你用JSON类型有什么意义？");

						c.one(ALOAD_1);
						c.ldc(new CstString(genType==null?type.toDesc():genType.toDesc()));
						c.invoke(INVOKESTATIC, SELF, "_JAdapt", "(L"+REQ+";Ljava/lang/String;)Ljava/lang/Object;");
						c.clazz(CHECKCAST, type);
						return;
					} else {
						throw new IllegalArgumentException("JSON类型仅能出现一次(这是反序列化请求体啊)");
					}
			}
			if ((bodyKind & 6) == 6) throw new IllegalArgumentException("不能同时使用POST_KV和JSON");

			if (fromSlot == 0) throw new IllegalStateException("不支持的类型/"+from1);

			c.vars(ALOAD, fromSlot);
			c.ldc(name);
			c.invokeItf("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

			TryCatchEntry entry = new TryCatchEntry();
			entry.start = cw.label();
			entry.handler = new Label();
			entry.type = type+" "+name;

			addExHandler:
			{
			if (type.owner != null || type.array() > 0) {
				if (type.owner != null) {
					if (type.owner.equals("java/lang/String") ||
						type.owner.equals("java/lang/CharSequence")) {
						if (field.getBoolean("nonnull", nonnull)) {
							cw.invoke(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
						} else {
							cw.clazz(CHECKCAST, type.owner);
						}
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
				cw.newObject("roj/io/FastFailException");
				cw.one(ATHROW);
				cw.label(label);
			} else {
				name = Type.toString(type.type);
				name = Character.toUpperCase(name.charAt(0))+name.substring(1);
				cw.clazz(CHECKCAST, "java/lang/String");
				cw.invoke(INVOKESTATIC, "java/lang/"+(name.equals("Int")?"Integer":name), "parse"+name, "(Ljava/lang/String;)"+(char)type.type);
			}
			}
			entry.end = cw.label();
			tries.add(entry);
		}
	}

	private static final class Found extends MutableInt implements BiFunction<MutableInt, Object, Boolean> {
		int action, maxLen;
		Route result;

		@Override
		public Boolean apply(MutableInt kPos, Object o) {
			Route v = getASet(o, action);
			if (((1<<action)&v.accepts) != 0) {
				if ((v.accepts&PREFIX) != 0 || kPos.getValue() == maxLen) result = v;
			}
			return false;
		}
	}

	private SimpleList<RouteMatcher> routeMatchers = new SimpleList<>();
	public static final class RouteMatcher {
		Route route;
		String prefix;
		Pattern regexp;
		String[] params;

		public RouteMatcher(String url) {

		}

		public String getPrefix() {
			return null;
		}
	}
	public static final class RouterResult {
		String[] params, groups;
	}
	public static final class Route {
		RouteMatcher matcher;
		int accepts;
		Dispatcher req;
		Dispatcher[] prec;
	}
	private static Route getASet(Object o, int action) { return o instanceof Route ? ((Route) o) : ((Route[]) o)[action]; }

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		String path = req.path();
		int len = path.length();
		while (len > 1 && path.charAt(len-1) == '/') len--;

		Route set;
		if (len == 0) {
			Object o = route.get("");
			set = o == null ? null : getASet(o, req.action());
		} else {
			Found found = (Found) req.threadContext().get("or:fn");
			if (found == null) req.threadContext().put("or:fn", found = new Found());

			found.action = req.action();
			found.maxLen = len;
			found.result = null;

			route.longestWithCallback(path, 0, len, found, found);

			set = found.result;
		}

		block:
		if (set == null) {
			for (int i = 0; i < routeMatchers.size(); i++) {
				RouteMatcher rm = routeMatchers.get(i);
				if (len <= rm.prefix.length()) continue;
				Matcher m = rm.regexp.matcher(path.substring(rm.prefix.length(), len));
				if (!m.matches()) continue;

				RouterResult r = new RouterResult();
				r.params = rm.params;
				r.groups = new String[r.params.length];
				for (int j = 0; j < r.groups.length;) {
					r.groups[j] = m.group(++j);
				}
				req.connection().attachment(RouterArgumentKey, r);
				set = rm.route;

				// can use for-else later
				break block;
			}
			throw new IllegalRequestException(403, "该路径没有匹配的路由");
		}
		if (((1 << req.action()) & set.accepts) == 0) throw new IllegalRequestException(HttpCode.METHOD_NOT_ALLOWED, "路由不接受这种方法");

		req.connection().attachment(RouteAdapterKey, set);
		for (Dispatcher prec : set.prec) {
			Object ret = prec.invoke(req, req.handler(), cfg);
			if (ret != null) {
				if (ret instanceof Response) throw new IllegalRequestException(200, ((Response) ret));
				throw new IllegalRequestException(200, ret.toString());
			}
		}

		if (cfg != null && !cfg.postAccepted()) Router.super.checkHeader(req, cfg);
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		Route set = req.connection().attachment(RouteAdapterKey, null);

		Object ret;
		try {
			ret = set.req.invoke(req, rh, null);
		} finally {
			for (Callable<Void> c : prCallback) {
				try {
					c.call();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (ret instanceof Response) return (Response) ret;
		if (ret == null) return null;
		return new StringResponse(ret.toString());
	}

	public static Object _JAdapt(Request req, String type) throws IllegalRequestException {
		if (req.postBuffer() == null) throw new IllegalRequestException(400, "没有请求体");

		CCJson jsonp = (CCJson) req.threadContext().get("or:jsonp");
		if (jsonp == null) req.threadContext().put("or:jsonp", jsonp = new CCJson());

		Serializer<?> adapter = Serializers.SAFE.serializer(Signature.parseGeneric(type));
		try {
			adapter.reset();
			jsonp.parse(req.postBuffer(), 0, adapter);
			return adapter.get();
		} catch (Exception e) {
			throw new IllegalRequestException(400, e.getMessage() == null ? "JSON数据无效" : e.getMessage(), e);
		}
	}

	interface Dispatcher {
		Object invoke(Request req, ResponseHeader srv, Object extra);
		default Dispatcher copyWith(int methodId, Object ref) { return this; }
	}
}