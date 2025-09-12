package roj.http.server.auto;

import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.annotation.AList;
import roj.asm.annotation.AnnVal;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.attr.ParameterAnnotations;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstString;
import roj.asm.insn.CodeWriter;
import roj.asm.insn.Label;
import roj.asm.insn.SwitchBlock;
import roj.asm.insn.TryCatchBlock;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asmx.mapper.ParamNameMapper;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.IntMap;
import roj.collect.ToIntMap;
import roj.concurrent.Task;
import roj.config.BinaryParser;
import roj.config.JsonParser;
import roj.config.MsgPackParser;
import roj.config.mapper.ObjectMapperFactory;
import roj.config.node.ConfigValue;
import roj.http.Headers;
import roj.http.HttpUtil;
import roj.http.server.*;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.Reflection;
import roj.reflect.VirtualReference;
import roj.text.HtmlEntities;
import roj.util.ByteList;
import roj.util.FastFailException;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static roj.asm.Opcodes.*;

/**
 * 注解定义的Http路由器.
 * 在函数上使用{@link roj.http.server.auto.Route Route}、{@link GET}或{@link POST}注解，将这个函数变为请求处理函数
 * 如果选择Route注解，还可以用{@link Accepts}注解允许多个请求方法，例如POST与GET；还可以使用前缀匹配路径而不是精确匹配
 * 上述注解使用函数名称.replace("__", "/")作为默认的匹配路径
 * 在请求处理函数的参数上使用{@link QueryParam}、{@link Body}、注解从请求的GET或POST字段中提取对应名称的数据，支持基本类型、字符串、以及从Post的JSON/MsgPack格式反序列化对象
 * 在处理函数上使用{@link Body}注解，来避免重复填写From属性
 * 在类或函数上使用{@link Mime}注解，这会影响返回字符串函数的MimeType，没有默认值，而不影响返回{@link Content}的.
 * 在类或函数上使用{@link Interceptor}注解，
 *   如果这是一个请求处理函数，或者在类上使用，那么为它增加预处理器（拦截器），它们会按定义顺序（类 + 函数）在接收完该请求的头部时调用，而处理函数会在整个请求接收完后再调用.
 *   否则，将这个函数注册为预处理函数
 *
 * @author solo6975
 * @since 2022/3/27 14:26
 */
public final class OKRouter implements Router {
	private static final String REQ = "roj/http/server/Request";
	private static final int ACCEPTS_ALL = 511;

	private final TypedKey<Route> RouterImplKey = new TypedKey<>("or:router");
	private final HashMap<String, Dispatcher> interceptors = new HashMap<>();

	private final Node route = new Text("");

	private final boolean debug;
	private List<Task> onFinishes = Collections.emptyList();

	public OKRouter() {this(true);}
	public OKRouter(boolean debug) {this.debug = debug;}

	/**
	 * 警告：如果使用addPrefixDelegation添加OKRouter，那么onFinish可能不会被触发
	 */
	public void onFinish(Task callback) {
		if (onFinishes.isEmpty()) onFinishes = new ArrayList<>();
		onFinishes.add(callback);
	}

	//region 代码生成
	/**
	 * 从注解生成用户路由器的调用实例(Dispatcher)以及方法ID映射
	 */
	private static final class CallerBuilder {
		private final boolean debug;

		private CodeWriter cw;
		private final List<TryCatchBlock> exceptionHandlers = new ArrayList<>();
		private final Annotation defaultSource = new Annotation();
		// slot0 this, slot1 request, slot2 handler
		private int slot, nextSlot = 3;
		// instance, req, rh
		private int bodyUsed;

		CallerBuilder(boolean debug) {this.debug = debug;}
		RouterInfo build(Class<?> type) {
			var userRoute = ClassNode.fromType(type);
			if (userRoute == null) throw new IllegalStateException("找不到"+type.getName()+"的类文件");

			var caller = this.cn = new ClassNode();
			caller.name(type.getName().replace('.', '/')+"$Router");
			caller.interfaces().add("roj/http/server/auto/OKRouter$Dispatcher");
			//caller.parent(Bypass.MAGIC_ACCESSOR_CLASS);
			//not needed, only invoke type.xxx
			//caller.putAttr(new AttrString("SourceFile", type.getName()));
			caller.npConstructor();

			caller.newField(ACC_PRIVATE, "$m", "I");
			caller.newField(ACC_PRIVATE, "$i", TypeHelper.class2asm(type));

			var cw = caller.newMethod(ACC_PUBLIC, "copyWith", "(ILjava/lang/Object;)Lroj/http/server/auto/OKRouter$Dispatcher;");
			cw.visitSize(2, 3);

			cw.newObject(caller.name());
			cw.insn(ASTORE_0);

			cw.insn(ALOAD_0);
			cw.insn(ILOAD_1);
			cw.field(PUTFIELD, caller, 0);

			cw.insn(ALOAD_0);
			cw.insn(ALOAD_2);
			cw.clazz(CHECKCAST, type.getName().replace('.', '/'));
			cw.field(PUTFIELD, caller, 1);

			cw.insn(ALOAD_0);
			cw.insn(ARETURN);
			cw.finish();

			cw = this.cw = caller.newMethod(ACC_PUBLIC, "invoke", "(L"+REQ+";Lroj/http/server/ResponseHeader;Ljava/lang/Object;)Ljava/lang/Object;");
			cw.visitSize(5, 4);

			cw.insn(ALOAD_0);
			cw.field(GETFIELD, caller, 1);

			cw.insn(ALOAD_0);
			cw.field(GETFIELD, caller, 0);

			var seg = SwitchBlock.ofSwitch(TABLESWITCH);
			cw.addSegment(seg);

			SwitchBlock seg2;
			CodeWriter cw2;
			if (debug) {
				cw2 = caller.newMethod(ACC_PUBLIC | ACC_FINAL, "toString", "()Ljava/lang/String;");
				cw2.visitSize(1, 1);
				cw2.insn(ALOAD_0);
				cw2.field(GETFIELD, caller, 0);
				seg2 = SwitchBlock.ofSwitch(TABLESWITCH);
				cw2.addSegment(seg2);
			} else {
				seg2 = null;
				cw2 = null;
			}

			int methodId = 0;
			IntMap<Annotation> handlers = new IntMap<>();
			ToIntMap<String> interceptors = new ToIntMap<>();

			var prependInterceptors = getPrependInterceptors(userRoute);

			var methods = userRoute.methods;
			for (int i = 0; i < methods.size(); i++) {
				var mn = methods.get(i);

				var map = parseAnnotations(Annotations.getAnnotations(userRoute.cp, mn, false));
				if (map == null) continue;

				if (map.type().equals("roj/http/server/auto/Interceptor")) {
					var value = map.getList("value");
					if (value.size() > 1) throw new IllegalArgumentException("作为预处理器函数的@Interceptor的values长度只能为0或1");

					var name = value.isEmpty() ? mn.name() : value.getString(0);
					Integer prev = interceptors.putInt(name, methodId++ | (map.getBool("global") ? Integer.MIN_VALUE : 0));
					if (prev != null) throw new IllegalArgumentException("预处理器名称重复: "+prev);
				} else {
					if (prependInterceptors != null) {
						var self = map.getList("interceptor");
						if (self.isEmpty()) map.put("interceptor", new AList(prependInterceptors));
						else self.raw().addAll(0, prependInterceptors);
					}

					handlers.put(methodId++, map);
					if (!map.containsKey("value"))
						map.put("value", AnnVal.valueOf(mn.name().replace("__", "/")));
				}

				Label self = cw.label();
				seg.branch(seg.cases.size(), self);
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
							cw.insn(ALOAD_1);
							break noBody;
						}

						if(!"roj/http/server/ResponseHeader".equals(par.get(1).owner)) {
							cw.insn(ALOAD_1);
							begin = 1;
							break hasBody;
						}

						cw.insn(ALOAD_1);
						cw.insn(ALOAD_2);
						if (par.size() <= 2) break noBody;
					}

					if (map.type().equals("roj/http/server/auto/Interceptor")) {
						cw.insn(ALOAD_3);
						cw.clazz(CHECKCAST, PostSetting.class.getName().replace('.', '/'));
					} else {
						defaultSource.put("source", map.getString("deserializeFrom", "UNDEFINED"));
						convertParams(userRoute.cp, mn, begin);
					}
				}

				cw.invoke(INVOKEVIRTUAL, mn.owner(), mn.name(), mn.rawDesc());
				if (mn.returnType().type != Type.CLASS) {
					if (mn.returnType().type != Type.VOID)
						throw new IllegalArgumentException("方法返回值必须是空值或对象:"+mn);
					else cw.insn(ACONST_NULL);
				}
				cw.insn(ARETURN);

				if (seg2 != null) {
					Label label = cw2.label();
					seg2.branch(seg2.cases.size(), label);
					seg2.def = label;
					cw2.ldc(mn.owner()+"."+mn.name()+mn.rawDesc());
					cw2.insn(ARETURN);
				}
			}
			if (seg.def == null) throw new IllegalArgumentException(userRoute.name()+"没有任何处理函数");

			List<TryCatchBlock> exceptionHandlers = this.exceptionHandlers;
			if (debug) {
				var map = new HashMap<String, Label>();
				for (var tce : exceptionHandlers) {
					var javacsb = cw;
					tce.handler.set(map.computeIfAbsent(tce.type, s -> {
						Label label = javacsb.label();
						javacsb.insn(ALOAD_1);
						javacsb.ldc(tce.type);
						javacsb.invoke(INVOKESTATIC, "roj/http/server/auto/OKRouter", "requestDebug", "(Ljava/lang/Throwable;Lroj/http/server/Request;Ljava/lang/String;)Lroj/http/server/IllegalRequestException;");
						javacsb.insn(ATHROW);
						return label;
					}));
					cw._addLabel(tce.handler);
				}
			} else {
				for (var tce : exceptionHandlers) {
					cw.label(tce.handler);
				}
				cw.field(GETSTATIC, "roj/http/server/IllegalRequestException", "BAD_REQUEST", "Lroj/http/server/IllegalRequestException;");
				cw.insn(ATHROW);
			}
			cw.visitExceptions();
			for (var tce : exceptionHandlers) cw.visitException(tce.start,tce.end,tce.handler,null);
			cw.finish();

			if (clinit != null) {
				clinit.insn(RETURN);
				clinit.finish();
			}

			var inst = (Dispatcher) ClassDefiner.newInstance(caller, type.getClassLoader());
			var defaultMime = Annotation.findInvisible(userRoute.cp, userRoute, "roj/http/server/auto/Mime");
			return new RouterInfo(handlers, interceptors, inst, defaultMime != null ? defaultMime.getString("value") : null);
		}

		// WARNING: clinit is wrong when ClassDefiner not use allocateInstance!
		private ClassNode cn;
		private final ToIntMap<String> typeFids = new ToIntMap<>();
		private CodeWriter clinit;
		private void loadType(String type) {
			int fid = typeFids.getOrDefault(type, -1);
			if (fid < 0) {
				fid = cn.newField(ACC_PRIVATE | ACC_STATIC | ACC_FINAL, "$"+cn.fields.size(), "Lroj/asm/type/IType;");
				typeFids.putInt(type, fid);

				if (clinit == null) {
					clinit = cn.newMethod(ACC_PUBLIC|ACC_STATIC, "<clinit>", "()V");
					clinit.visitSize(1, 0);
				}

				clinit.ldc(new CstString(type));
				clinit.invokeS("roj/asm/type/Signature", "parseGeneric", "(Ljava/lang/CharSequence;)Lroj/asm/type/IType;");
				clinit.field(PUTSTATIC, cn, fid);
			}

			cw.field(GETSTATIC, cn, fid);
		}

		private void convertParams(ConstantPool cp, MethodNode method, int begin) {
			List<List<Annotation>> annos = ParameterAnnotations.getParameterAnnotation(cp, method, false);
			if (annos == null) annos = Collections.emptyList();

			Signature signature = method.getAttribute(cp, Attribute.SIGNATURE);
			List<IType> genTypes = signature == null ? Collections.emptyList() : signature.values;

			List<String> parNames = ParamNameMapper.getParameterNames(cp, method);
			if (parNames == null) parNames = Collections.emptyList();

			slot = 0;
			nextSlot = 3;
			bodyUsed = 0;

			List<Type> parTypes = method.parameters();
			for (; begin < parTypes.size(); begin++) {
				convertParam(begin>=annos.size()?null:parseParameterAnnotations(annos.get(begin)),
						begin>=parNames.size()?null:parNames.get(begin),
						parTypes.get(begin),
						begin>=genTypes.size() ? null : genTypes.get(begin));
			}

			cw.visitSizeMax(TypeHelper.paramSize(method.rawDesc())+3, nextSlot);
		}
		private void convertParam(@Nullable Annotation field, String name, Type rawType, IType type) {
			if (field == null) field = defaultSource;
			name = field.getString("value", name);
			if (type == null) type = rawType;

			String source = field.getString("source");
			int fromSlot = 0;

			if (name == null && !source.equals("BODY")) throw new IllegalArgumentException("编译时是否保存了方法参数名称？");

			CodeWriter c = cw;
			switch (source) {
				case "HEAD" -> fromSlot = 1;
				case "POST" -> {
					fromSlot = (slot >>> 8) & 0xFF;
					if (fromSlot == 0) {
						bodyUsed |= 2;

						c.insn(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "formData", "()Ljava/util/Map;");
						c.vars(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++ << 8;
					}
				}
				case "GET" -> {
					fromSlot = (slot) & 0xFF;
					if (fromSlot == 0) {
						bodyUsed |= 1;

						c.insn(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "queryParam", "()Ljava/util/Map;");
						c.vars(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++;
					}
				}
				case "COOKIE" -> {
					fromSlot = (slot >>> 16) & 0xFF;
					if (fromSlot == 0) {
						c.insn(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "cookie", "()Ljava/util/Map;");
						c.vars(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++ << 16;
					}
				}
				case "BODY" -> {
					if ((bodyUsed & 4) == 0) {
						bodyUsed |= 4;

						if (rawType.getActualType() != Type.CLASS)
							throw new IllegalArgumentException("基本类型无法使用JSON解析");

						var tce = new TryCatchBlock();
						tce.start = cw.label();
						tce.handler = new Label();
						tce.type = type+" "+name;

						c.insn(ALOAD_1);
						loadType(type.toDesc());
						c.invoke(INVOKESTATIC, "roj/http/server/auto/OKRouter", "parse", "(L"+REQ+";Lroj/asm/type/IType;)Ljava/lang/Object;");
						c.clazz(CHECKCAST, rawType);

						tce.end = cw.label();
						exceptionHandlers.add(tce);
						return;
					} else {
						throw new IllegalArgumentException("JSON类型仅能出现一次(这是反序列化请求体啊)");
					}
				}
			}
			if ((bodyUsed & 6) == 6) throw new IllegalArgumentException("不能同时使用POST和BODY类型");

			var tce = new TryCatchBlock();
			tce.start = cw.label();
			tce.handler = new Label();
			tce.type = type+" "+name;

			if (source.equals("PARAM")) {
				c.insn(ALOAD_1);
				// 这个不需要解析，所以也不需要缓存到本地变量
				c.invoke(INVOKEVIRTUAL, REQ, "arguments", "()Lroj/http/Headers;");
			} else {
				if (fromSlot == 0) throw new IllegalStateException("不支持的类型:"+source);
				c.vars(ALOAD, fromSlot);
			}

			c.ldc(name);
			String orDefault = field.getString("orDefault", null);
			if (orDefault != null) {
				c.ldc(orDefault);
				c.invokeItf("java/util/Map", "getOrDefault", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
			} else {
				c.invokeItf("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");
				if (!field.getBool("optional", false)) {
					cw.insn(DUP);
					var label = new Label();
					cw.jump(IFNONNULL, label);
					cw.insn(POP);
					cw.clazz(NEW, "roj/util/FastFailException");
					cw.insn(DUP);
					cw.ldc("参数缺失");
					cw.invokeD("roj/util/FastFailException", "<init>", "(Ljava/lang/String;)V");
					cw.insn(ATHROW);
					cw.label(label);
				}
			}

			addExHandler: {
				int type1 = rawType.getActualType();
				if (type1 == Type.CLASS) {
					if (rawType.owner != null) {
						if (rawType.owner.equals("java/lang/String") || rawType.owner.equals("java/lang/CharSequence")) {
							cw.invoke(INVOKEVIRTUAL, "java/lang/Object", "toString", "()Ljava/lang/String;");
							break addExHandler;
						}
					}
					throw new IllegalArgumentException("无法将String转换为"+rawType);
				}
				if (type1 == Type.CHAR) {
					// stack=4;
					cw.invoke(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I");
					cw.insn(DUP);
					cw.insn(DUP);
					cw.insn(I2C);
					Label label = new Label();
					cw.jump(IF_icmpeq, label);
					cw.clazz(NEW, "roj/util/FastFailException");
					cw.insn(DUP);
					cw.ldc("参数超出范围");
					cw.invokeD("roj/util/FastFailException", "<init>", "(Ljava/lang/String;)V");
					cw.insn(ATHROW);
					cw.label(label);
				} else {
					name = Reflection.upper(Type.getName(type1));
					cw.clazz(CHECKCAST, "java/lang/String");
					cw.invoke(INVOKESTATIC, "java/lang/"+(name.equals("Int")?"Integer":name), "parse"+name, "(Ljava/lang/String;)"+(char)rawType.type);
				}
			}
			tce.end = cw.label();
			exceptionHandlers.add(tce);
		}

		@Nullable
		private static List<ConfigValue> getPrependInterceptors(ClassNode data) {
			var preDef = Annotation.findInvisible(data.cp, data, "roj/http/server/auto/Interceptor");
			return preDef != null ? preDef.getList("value").raw() : null;
		}
		private static Annotation parseAnnotations(List<Annotation> list) {
			ConfigValue accepts = null, mime = null;
			Annotation interceptor = null, route = null, easyMapping = null;

			for (int j = 0; j < list.size(); j++) {
				var a = list.get(j);
				switch (a.type()) {
					case "roj/http/server/auto/Interceptor" -> interceptor = a;
					case "roj/http/server/auto/Route" -> route = a;
					case "roj/http/server/auto/Accepts" -> accepts = a.get("value");
					case "roj/http/server/auto/Mime" -> mime = a.get("value");
					case "roj/http/server/auto/GET", "roj/http/server/auto/POST" -> {
						if (easyMapping != null) throw new IllegalArgumentException(easyMapping+"不能和"+a+"共用");
						easyMapping = a;
					}
				}
			}

			if (easyMapping != null) {
				if (route != null) throw new IllegalArgumentException(easyMapping+"不能和@Route共用");
				if (accepts != null) throw new IllegalArgumentException(easyMapping+"不能和@Accepts共用");

				boolean isGet = easyMapping.type().endsWith("GET");
				easyMapping.putIfAbsent("deserializeFrom", isGet ? "GET" : "POST");
				easyMapping.put("accepts", AnnVal.valueOf(isGet ? Accepts.GET : Accepts.POST));

				route = easyMapping;
				route.setType("roj/http/server/auto/Route");
			} else if (route == null) return interceptor;

			if (interceptor != null) route.put("interceptor", interceptor.get("value"));
			if (accepts != null) route.put("accepts", accepts);
			if (mime != null) route.put("mime", mime);

			return route;
		}
		private static Annotation parseParameterAnnotations(List<Annotation> list) {
			Annotation holder = null;
			String source = null;
			boolean optional = false;
			long min = Long.MIN_VALUE;
			long max = Long.MAX_VALUE;

			for (int j = 0; j < list.size(); j++) {
				var a = list.get(j);
				switch (a.type()) {
					default -> {continue;}
					case "roj/http/server/auto/RequestParam" -> {
						if (holder != null) throw new IllegalStateException("注解"+a+"与已有的"+holder+"重复");
						source = "PARAM";
						optional = true; //实际上是不可空？
					}
					case "roj/http/server/auto/Header" -> {
						if (holder != null) throw new IllegalStateException("注解"+a+"与已有的"+holder+"重复");
						source = "HEAD";
					}
					case "roj/http/server/auto/QueryParam" -> {
						if (holder != null) throw new IllegalStateException("注解"+a+"与已有的"+holder+"重复");
						source = "GET";
					}
					case "roj/http/server/auto/Body" -> {
						if (holder != null) throw new IllegalStateException("注解"+a+"与已有的"+holder+"重复");
						source = "BODY";
					}
					case "org/jetbrains/annotations/Nullable" -> {
						optional = true;
						continue;
					}
					case "org/jetbrains/annotations/Range" -> {
						min = a.getLong("min");
						max = a.getLong("max");
						continue;
					}
				}

				holder = a;
			}

			if (holder == null) return null;

			holder.put("source", source);
			if (optional) holder.put("optional", true);

			return holder;
		}
	}
	// 生成请求调试信息
	@ReferenceByGeneratedClass
	public static IllegalRequestException requestDebug(Throwable exc, Request req, String msg) {
		var isBrowserRequest = Headers.getOneValue(req.get("accept"), "text/html") != null;
		return new IllegalRequestException(400, /*isBrowserRequest ? */Content.internalError("参数'"+HtmlEntities.escapeHtml(msg)+"'解析失败", exc));
	}
	// 解析JSON请求体
	@ReferenceByGeneratedClass
	public static Object parse(Request req, IType type) throws Exception {
		ByteList body = req.body();
		if (body == null) throw new FastFailException("没有请求体");

		var serializer = ObjectMapperFactory.SAFE.serializer(type); serializer.reset();
		BinaryParser parser;

		switch (req.getFirstHeaderValue("content-type")) {
			default -> {
				parser = (BinaryParser) req.threadLocal().get("or:parser:json");
				if (parser == null) req.threadLocal().put("or:parser:json", parser = new JsonParser());
			}
			case "application/x-msgpack"/* Unofficial */, "application/vnd.msgpack" -> {
				parser = (BinaryParser) req.threadLocal().get("or:parser:msgpack");
				if (parser == null) req.threadLocal().put("or:parser:msgpack", parser = new MsgPackParser());
			}
			case "application/x-www-form-urlencoded", "multipart/form-data" -> {
				var data = req.formData();
				serializer.emitMap(data.size());
				for (Map.Entry<String, String> entry : data.entrySet()) {
					serializer.key(entry.getKey());
					serializer.emit(entry.getValue());
				}
				serializer.pop();
				return serializer.get();
			}
		}

		body.retain();
		parser.parse(body, 0, serializer);
		return serializer.get();
	}
	//endregion
	private static final VirtualReference<HashMap<String, RouterInfo>> IMPLEMENTATION_CACHE = new VirtualReference<>();
	private static final class RouterInfo {
		final IntMap<Annotation> handlers;
		final ToIntMap<String> interceptors;
		final Dispatcher inst;
		final String defaultMime;

		RouterInfo(IntMap<Annotation> handlers, ToIntMap<String> interceptors, Dispatcher inst, String defaultMime) {
			this.handlers = handlers;
			this.interceptors = interceptors;
			this.inst = inst;
			this.defaultMime = defaultMime;
		}
	}

	public final OKRouter register(Object o) {return register(o, "");}
	public final OKRouter register(Object o, String pathRel) {
		var type = o.getClass();
		var map = IMPLEMENTATION_CACHE.computeIfAbsent(type.getClassLoader(), Helpers.cast(Helpers.fnHashMap()));
		var inst = map.get(type.getName());
		if (inst == null) {
			synchronized (map) {
				if ((inst = map.get(type.getName())) == null) {
					inst = new CallerBuilder(debug).build(type);
					map.put(type.getName(), inst);
				}
			}
		}

		// 存放已经实例化的拦截器
		HashMap<String, Dispatcher> interceptorInstance = new HashMap<>();
		for (var entry : inst.handlers.selfEntrySet()) {
			int i = entry.getIntKey();
			var annotation = entry.getValue();
			var subroute = new Route();

			subroute.accepts = annotation.getInt("accepts", 3/* GET|POST */);
			subroute.req = inst.inst.copyWith(i, o);
			subroute.mime = annotation.getString("mime", inst.defaultMime);

			List<Dispatcher> precs = Collections.emptyList();
			var interceptorNames = annotation.getList("interceptor");
			if (!interceptorNames.isEmpty()) {
				for (int j = 0; j < interceptorNames.size(); j++) {
					String name = interceptorNames.getString(j);

					var interceptor = interceptorInstance.get(name);
					if (interceptor == null) {
						int methodId = inst.interceptors.getOrDefault(name, -1);
						if (methodId == -1) {
							if ((interceptor = interceptors.get(name)) == null) {
								// 忽略这个拦截器
								if (interceptors.containsKey(name)) continue;
								throw new IllegalArgumentException("未找到"+annotation+"引用的拦截器"+interceptorNames.get(j));
							}
						} else {
							interceptor = inst.inst.copyWith(methodId & Integer.MAX_VALUE, o);
						}
						interceptorInstance.put(name, interceptor);
					}

					if (precs.size() == 0) {
						precs = Collections.singletonList(interceptor);
					} else {
						if (precs.size() == 1) precs = new ArrayList<>(precs);
						precs.add(interceptor);
					}
				}
			}
			subroute.prec = precs.toArray(new Dispatcher[precs.size()]);

			int flag = annotation.getBool("prefix")?Node.PREFIX:0;
			String subpath = annotation.getString("value");
			if (subpath.endsWith("/**")) {
				flag |= Node.PREFIX;
				subpath = subpath.substring(0, subpath.length()-2);
			}
			String url = pathRel.concat(subpath);
			if (url.endsWith("/")) flag |= Node.DIRECTORY;
			else if (annotation.getBool("strict")) flag |= Node.FILE;

			Node node = route.add(url, 0, url.length());

			Object prev = node.value;
			if (prev == null) {
				node.flag |= (byte) flag;
				node.value = subroute;
			} else {
				if (node.flag != flag) throw new IllegalArgumentException("prefix定义不同/"+o+" in "+prev+"|"+subroute);

				if (prev instanceof Route prevReq) {
					Route[] newReq = new Route[8];
					node.value = newReq;

					if ((prevReq.accepts & subroute.accepts) != 0)
						throw new IllegalArgumentException("冲突的请求类型处理器/"+o+" in "+prevReq+"|"+subroute+" accepts="+prevReq.accepts+"|"+subroute.accepts);

					for (int j = 0; j < 8; j++) {
						if ((subroute.accepts & (1<<j)) != 0) {
							newReq[j] = subroute;
						} else if ((prevReq.accepts & (1<<j)) != 0) {
							newReq[j] = prevReq;
						}
					}
				} else {
					Route[] newReq = ((Route[]) prev);
					for (int j = 0; j < 8; j++) {
						if ((subroute.accepts & (1<<j)) != 0) {
							if (newReq[j] != null)
								throw new IllegalArgumentException("冲突的请求类型处理器/"+o+" in "+newReq[j]+"|"+subroute+":"+HttpUtil.getMethodName(j));
							newReq[j] = subroute;
						}
					}
				}
			}
		}

		for (var entry : inst.interceptors.selfEntrySet()) {
			if ((entry.value & Integer.MIN_VALUE) != 0) {
				String name = entry.getKey();
				if (interceptors.containsKey(name)) throw new IllegalStateException(o+"的"+name+"拦截器在当前上下文重复");
				interceptors.put(name, inst.inst.copyWith(entry.value & Integer.MAX_VALUE, o));
			}
		}
		return this;
	}

	public final Dispatcher getInterceptor(String name) {return interceptors.get(name);}
	public final void setInterceptor(String name, Dispatcher interceptor) {interceptors.put(name, interceptor);}
	public final void removeInterceptor(String name) {interceptors.remove(name);}

	/**
	 * @see #addPrefixDelegation(String, Router, String...)
	 */
	public final OKRouter addPrefixDelegation(String path, Router router) {return addPrefixDelegation(path, router, (String[])null);}

	/**
	 * 注册path/**拦截器
	 * 路径结尾是否有斜杠也会影响策略
	 * @param path
	 * @param router
	 * @param interceptors
	 * @return
	 */
	public final OKRouter addPrefixDelegation(String path, Router router, @Nullable String... interceptors) {
		Node node = route.add(path, 0, path.length());
		if (node.value != null) throw new IllegalArgumentException("子路径'"+path+"'已存在");

		var aset = new Route();

		node.flag |= Node.PREFIX|(path.endsWith("/")?Node.DIRECTORY:0);
		node.value = aset;
		aset.accepts = ACCEPTS_ALL;

		if (interceptors == null || interceptors.length == 0) {
			if (router instanceof OKRouter child) {
				Node otherRoot = child.route;
				node.flag = otherRoot.flag;
				node.value = otherRoot.value;
				node.table = otherRoot.table;
				node.size = otherRoot.size;
				node.mask = otherRoot.mask;
				node.any = otherRoot.any;
				//prependInterceptorArray(interceptors);
				return this;
			}

			aset.prec = new Dispatcher[] {_getChecker(router)};
		} else {
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
			prec[size] = _getChecker(router);
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

		if (router instanceof Predicate<?>) {
			aset.earlyCheck = Helpers.cast(router);
		}

		return this;
	}
	private static Dispatcher _getChecker(Router router) {
		return (req, srv, extra) -> {
			router.checkHeader(req, (PostSetting) extra);
			return null;
		};
	}

	public final boolean removePrefixDelegation(String path) {return route.remove(path, 0, path.length());}

	private static final class Route {
		//bitset for http method
		int accepts;
		//accept this path for prefix router
		Predicate<String> earlyCheck = Helpers.alwaysTrue();
		String mime;
		Dispatcher req;
		Dispatcher[] prec;

		@Override
		public String toString() {return "processor="+req;}
	}
	private static Route getRoute(Object o, int action) { return o instanceof Route ? ((Route) o) : ((Route[]) o)[action]; }

	private static final class RouteMatcher {
		private final ArrayList<Node> env1 = new ArrayList<>(), env2 = new ArrayList<>();
		private final ArrayList<ArrayList<String>> par3 = new ArrayList<>(), par4 = new ArrayList<>();

		int prefixLen;
		Object value;

		private Node prefixNode;
		private ArrayList<String> prefixPar;
		private int prefixParSize;
		private boolean definitivelyMatch;

		boolean methodNotAllowedMatch;
		int allowMethod;

		final boolean match(Request req, Node root, String path, int i, int end) {
			var nodeS = env1;
			var nodeD = env2;
			var parS = par3;
			var parD = par4;

			nodeS.add(root);

			prefixNode = null;
			prefixPar = null;
			definitivelyMatch = false;

			int prevI = 0;
			while (i < end) {
				int nextI = path.indexOf('/', i);
				if (nextI >= end || nextI < 0) nextI = end;

				for (int k = 0; k < nodeS.size(); k++) {
					Node node = nodeS.get(k);

					if ((node.flag & Node.PREFIX) != 0 && checkPrefixMatch(node, path, i)) {
						prefixLen = i;
						prefixNode = node;
						prefixPar = parS.size() <= k ? null : parS.get(k);
						if (prefixPar != null) prefixParSize = prefixPar.size();
					}

					int pos = nodeD.size();
					String parName = node.get(path, i, nextI, nodeD);

					syncParam:
					if (nodeD.size() > pos) {
						ArrayList<String> params = parS.size() <= k ? null : parS.get(k);
						if (parName != null) {
							if (params == null) params = new ArrayList<>();
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
					return buildPrefix(req);
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
			int priority = definitivelyMatch ? 5 : -1;
			ArrayList<String> par = null;

			boolean isFile = path.charAt(end - 1) != '/';
			for (int j = 0; j < nodeS.size(); j++) {
				Node n = nodeS.get(j);
				if (n.value != null) {
					// 目录和文件匹配
					if ((n.flag & Node.DIRECTORY) != 0) {
						if (isFile) continue;
					} else if ((n.flag & Node.FILE) != 0) {
						if (!isFile) continue;
					}

					int prio = n.priority();
					if (prio > priority) {
						int accepts = getRoute(n.value, req.action()).accepts;
						allowMethod = accepts;
						methodNotAllowedMatch = ((1 << req.action()) & accepts) == 0;
						if (methodNotAllowedMatch) continue;

						node = n;
						priority = prio;
						par = parS.size() <= j ? null : parS.get(j);

						if (node instanceof Regex rex) {
							if (par == null) par = new ArrayList<>();

							par.add(rex.name);
							par.add(path.substring(prevI, isFile ? end : end-1));
						}
					} else if (prio == priority && node != null) { // equals
						throw new IllegalStateException("该路径被多个请求处理器命中: "+node+"|"+n);
					}
				}
			}

			parS.clear();
			nodeS.clear();

			if (node == null) return buildPrefix(req);

			// 兼容之前的代码，非严格模式
			if ((node.flag&Node.DIRECTORY) == 0 && !isFile) end--;
			prefixLen = end;
			value = getNodeValue(node);
			if (par != null) {
				var pathVariable = req.arguments();
				int j = 0;
				while (j < par.size()) {
					pathVariable.add(par.get(j++), par.get(j++));
				}
			}
			return true;
		}

		private boolean checkPrefixMatch(Node node, String path, int i) {
			Object v = getNodeValue(node);
			return v instanceof Route r && (definitivelyMatch = r.earlyCheck.test(path.substring(i)));
		}

		private boolean buildPrefix(Request req) {
			methodNotAllowedMatch = false;
			if (prefixNode == null) return false;
			value = getNodeValue(prefixNode);

			var par = prefixPar;
			if (par != null) {
				var pathVariable = req.arguments();
				int i = 0;
				while (i < prefixParSize) {
					pathVariable.add(par.get(i++), par.get(i++));
				}
			}

			prefixNode = null;
			prefixPar = null;
			return true;
		}
		private static Object getNodeValue(Node node) {
			var v = node.value;
			while (v instanceof Node n) {
				v = n.value;
			}
			return v;
		}
	}
	private static abstract sealed class Node {
		String name;
		// Route or Route[]
		Object value;

		byte flag;
		static final byte PREFIX = 1, DIRECTORY = 2, FILE = 4;

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
				if (any.isEmpty()) any = new ArrayList<>();

				i = any.indexOf(node2);
				if (i >= 0) node1 = any.get(i);
				else {
					if ((node2.flag&8) != 0 && j >= end) {
						if (value != null) throw new IllegalStateException("Already have "+value);
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

		static final Pattern REGEXP = Pattern.compile("([A-Za-z0-9-_]+)(?:\\((.+?)\\))?([+*?])?");
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
			set = o == null ? null : getRoute(o, req.action());
		} else {
			var m = (RouteMatcher) req.threadLocal().get("or:fn");
			if (m == null) req.threadLocal().put("or:fn", m = new RouteMatcher());

			if (m.match(req, route, path, 0, len) && m.value != null) {
				set = getRoute(m.value, req.action());
				req.setPath(path.substring(m.prefixLen));
			} else {
				if (m.methodNotAllowedMatch) {
					req.responseHeader().put("allow", serializeAllow(m.allowMethod));
					throw new IllegalRequestException(HttpUtil.METHOD_NOT_ALLOWED);
				}

				set = null;
			}
		}

		if (set == null) throw new IllegalRequestException(403);
		if (((1 << req.action()) & set.accepts) == 0) {
			req.responseHeader().put("allow", serializeAllow(set.accepts));
			throw new IllegalRequestException(HttpUtil.METHOD_NOT_ALLOWED);
		}

		req.connection().attachment(RouterImplKey, set);
		for (Dispatcher prec : set.prec) {
			Object ret = prec.invoke(req, req.server(), cfg);
			if (ret != null) {
				if (ret instanceof Content r) throw new IllegalRequestException(0, r);
				throw new IllegalRequestException(0, ret.toString());
			}
		}

		if (cfg != null && !cfg.postAccepted()) Router.super.checkHeader(req, cfg);
	}
	private static String serializeAllow(int accepts) {
		var sb = IOUtil.getSharedByteBuf();
		for (int i = 0; i < 8; i++) {
			if (((1<<i) & accepts) != 0) {
				sb.putAscii(HttpUtil.getMethodName(i)).putAscii(", ");
			}
		}

		if(sb.wIndex() > 0) sb.wIndex(sb.wIndex()-2);
		return sb.toString();
	}

	@Override
	public Content response(Request req, ResponseHeader rh) throws IOException {
		Route set = req.connection().attachment(RouterImplKey, null);

		Object ret;
		try {
			ret = set.req.invoke(req, rh, null);
		} finally {
			for (var c : onFinishes) {
				try {
					c.run();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		if (ret instanceof Content r) return r;
		if (ret == null) return null;
		return new TextContent(ret instanceof CharSequence cs ? cs : ret.toString(), set.mime);
	}

	public interface Dispatcher {
		Object invoke(Request req, ResponseHeader server, Object argument) throws IllegalRequestException;
		default Dispatcher copyWith(int methodId, Object instance) { return this; }
	}
}