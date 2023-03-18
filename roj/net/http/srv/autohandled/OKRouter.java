package roj.net.http.srv.autohandled;

import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnValString;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.SwitchEntry;
import roj.asm.type.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttrHelper;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.SwitchSegment;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.collect.TrieTree;
import roj.config.CCJson;
import roj.config.JSONParser;
import roj.config.serial.CAdapter;
import roj.config.serial.SerializerManager;
import roj.mapper.ParamNameMapper;
import roj.math.MutableInt;
import roj.net.http.Action;
import roj.net.http.IllegalRequestException;
import roj.net.http.srv.*;
import roj.reflect.FastInit;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static roj.asm.Opcodes.*;

/**
 * @author solo6975
 * @since 2022/3/27 14:26
 */
public class OKRouter implements Router {
	private static final String REQ = Request.class.getName().replace('.', '/');
	private static final String RESP = ResponseHeader.class.getName().replace('.', '/');

	private static final String SELF = "roj/net/http/srv/autohandled/OKRouter";
	private static final String INVOKE_DESC = TypeHelper.class2asm(new Class<?>[] {Request.class, ResponseHeader.class, Object.class}, Object.class);
	private static final String COPYWITH_DESC = TypeHelper.class2asm(new Class<?>[] {int.class, Object.class}, Dispatcher.class);

	private static final AtomicInteger seq = new AtomicInteger();

	// ASet or ASet[]
	private final TrieTree<Object> route = new TrieTree<>();

	public OKRouter() {}

	public final Router register(Object o) {
		ConstantData hndInst = new ConstantData();
		hndInst.name("roj/net/http/srv/autohandled/$Route$"+seq.incrementAndGet());
		hndInst.interfaces().add("roj/net/http/srv/autohandled/OKRouter$Dispatcher");
		FastInit.prepare(hndInst);

		hndInst.newField(0, "$methodId", "I");
		hndInst.newField(0, "$handler", TypeHelper.class2asm(o.getClass()));

		CodeWriter cw = hndInst.newMethod(AccessFlag.PUBLIC, "copyWith", COPYWITH_DESC);
		cw.visitSize(3, 3);
		cw.newObject(hndInst.name);

		cw.one(DUP);
		cw.one(ILOAD_1);
		cw.field(PUTFIELD, hndInst, 0);

		cw.one(DUP);
		cw.one(ALOAD_2);
		cw.clazz(CHECKCAST, o.getClass().getName().replace('.', '/'));
		cw.field(PUTFIELD, hndInst, 1);

		cw.one(ARETURN);
		cw.finish();

		cw = hndInst.newMethod(AccessFlag.PUBLIC, "invoke", INVOKE_DESC);
		cw.visitSize(5, 4);
		cw.one(ALOAD_0);
		cw.field(GETFIELD, hndInst, 1);
		cw.one(ALOAD_1);
		cw.one(ALOAD_2);
		cw.one(ALOAD_0);
		cw.field(GETFIELD, hndInst, 0);
		SwitchSegment seg = CodeWriter.newSwitch(TABLESWITCH);
		cw.switches(seg);

		int id = 0;
		IntMap<Annotation> handlers = new IntMap<>();
		ToIntMap<String> validator = new ToIntMap<>();

		String fn = o.getClass().getName().replace('.', '/').concat(".class");
		ConstantData data = Parser.parseConstants(o.getClass());

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);

			List<Annotation> list = AttrHelper.getAnnotations(data.cp, mn, false);
			Annotation a = AttrHelper.getAnnotation(list, "roj/net/http/srv/autohandled/Route");
			if (a == null) {
				a = AttrHelper.getAnnotation(list, "roj/net/http/srv/autohandled/Validator");
				if (a == null) continue;

				validator.putInt(a.getString("value", mn.name()), id++);
			} else {
				handlers.putInt(a, id++);
				if (a.getString("value") == null)
					a.put("value", new AnnValString(mn.name().replace("__", "/")));
			}

			Label self = cw.label();
			seg.targets.add(new SwitchEntry(seg.targets.size(), self));
			seg.def = self;
			List<Type> par = mn.parameters();

			noBody:{
				int begin = 2;

				hasBody:
				if (par.size() <= 2) {
					if (par.isEmpty()) {
						cw.one(POP2);

						break noBody;
					}

					if (!REQ.equals(par.get(0).owner)) {
						cw.one(POP2);

						begin = 0;
						break hasBody;
					}

					if (par.size() == 1) {
						cw.one(POP);
						break noBody;
					}

					if(!RESP.equals(par.get(1).owner)) {
						cw.one(POP);

						begin = 1;
						break hasBody;
					}

					break noBody;
				}

				if (a.clazz.endsWith("Validator")) {
					cw.one(ALOAD_3);
					cw.clazz(CHECKCAST, PostSetting.class.getName().replace('.', '/'));
				} else {
					provideBodyPars(cw, data.cp, mn, begin);
				}
			}

			cw.invoke(INVOKEVIRTUAL, mn.ownerClass(), mn.name(), mn.rawDesc());
			if (mn.returnType().type != Type.CLASS) {
				if (mn.returnType().type != Type.VOID)
					throw new IllegalArgumentException("方法返回值不是void或对象:"+mn);
				else cw.one(ACONST_NULL);
			}
			cw.one(ARETURN);

			Annotation a1 = AttrHelper.getAnnotation(list, "roj/net/http/srv/autohandled/Accepts");
			if (a1 != null) a.put("accepts", a1.values.get("value"));
		}

		Dispatcher ah = (Dispatcher) FastInit.make(hndInst);
		for (int i = 0; i < handlers.size(); i++) {
			Annotation a = handlers.get(i);
			ASet set = new ASet();

			set.accepts = a.getInt("accepts", -1);
			set.req = ah.copyWith(i, o);

			String vname = a.getString("validator", "");
			if (!vname.equals("")) {
				int vid = validator.getOrDefault(vname, -1);
				if (vid < 0) throw new IllegalArgumentException("未找到验证器/"+a);
				set.prec = ah.copyWith(vid, o);
			}

			String type = a.getEnumValue("type", "ASIS");
			set.prefix = type.equals("PREFIX");

			String url = a.getString("value");
			if (!set.prefix && url.endsWith("/"))
				url = url.substring(0, url.length()-1);

			Object prev = route.putIfAbsent(url, set);
			if (prev instanceof ASet) {
				ASet prevReq = ((ASet) prev);
				ASet[] newReq = new ASet[8];

				if ((prevReq.accepts & set.accepts) != 0)
					throw new IllegalArgumentException("冲突的请求类型处理器/"+o + " in "+prevReq+"|"+set);

				for (int j = 0; j < 8; j++) {
					if ((set.accepts & (1<<j)) != 0) {
						newReq[j] = set;
					} else if ((prevReq.accepts & (1<<j)) != 0) {
						newReq[j] = prevReq;
					}
				}

				route.put(url, newReq);
			} else if (prev != null) {
				ASet[] newReq = ((ASet[]) prev);
				for (int j = 0; j < 8; j++) {
					if ((set.accepts & (1<<j)) != 0) {
						if (newReq[j] != null)
							throw new IllegalArgumentException("冲突的请求类型处理器/"+o + " in "+newReq[j]+"|"+set+":"+Action.toString(j));
						newReq[j] = set;
					}
				}
			}
		}

		return this;
	}

	private void provideBodyPars(CodeWriter c, ConstantPool cp, MethodNode m, int begin) {
		List<Type> parTypes = m.parameters();

		Annotation body = AttrHelper.getAnnotation(AttrHelper.getAnnotations(cp, m, false), "roj/net/http/srv/autohandled/Body");
		if (body == null) throw new IllegalArgumentException("没有@Body注解 " + m);

		List<List<Annotation>> annos = AttrHelper.getParameterAnnotation(cp, m, false);
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

		for (; begin < parTypes.size(); begin++) {
			bw.process(cp,
				begin>=annos.size()?null:AttrHelper.getAnnotation(annos.get(begin), "roj/net/http/srv/autohandled/Field"),
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
		int stackSize = 3, maxStackSize = 3;
		int bodyKind;
		CodeWriter cw;
		String from;

		private static final Annotation DEFAULT = new Annotation();

		void process(ConstantPool cp, @Nullable Annotation field, String name, Type type, Generic genType) {
			if (field == null) field = DEFAULT;

			String from1 = field.getEnumValue("from", "INHERIT");
			if (from1.equals("INHERIT")) from1 = from;
			int fromSlot = 0;

			if (name == null && field.getString("value") == null && !from1.equals("JSON"))
				throw new IllegalArgumentException("编译时是否保存了方法参数名称？");

			CodeWriter c = cw;
			switch (from1) {
				case "POST_KV":
					fromSlot = (slot >>> 8) & 0xFF;
					if (fromSlot == 0) {
						bodyKind |= 2;

						c.one(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "postFields", "()Ljava/util/Map;");
						c.var(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++ << 8;
					}
				break;
				case "GET":
					fromSlot = (slot) & 0xFF;
					if (fromSlot == 0) {
						bodyKind |= 1;

						c.one(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "getFields", "()Ljava/util/Map;");
						c.var(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++;
					}
					break;
				case "REQUEST":
					fromSlot = (slot >>> 16) & 0xFF;
					if (fromSlot == 0) {
						bodyKind |= 3;

						c.one(ALOAD_1);
						c.invoke(INVOKEVIRTUAL, REQ, "fields", "()Ljava/util/Map;");
						c.var(ASTORE, fromSlot = nextSlot);
						slot |= nextSlot++ << 16;
					}
					break;
				case "JSON":
					if ((bodyKind & 4) == 0) {
						bodyKind |= 4;

						if (type.getActualType() != Type.CLASS) throw new IllegalArgumentException("那你用JSON类型有什么意义？");
						if (name != null) throw new IllegalArgumentException("那你用JSON类型有什么意义？");

						c.one(ALOAD_1);
						c.ldc(new CstString(genType==null?type.toDesc():genType.toDesc()));
						c.invoke(INVOKESTATIC, SELF, "_JAdapt", "("+REQ+"Ljava/lang/String;)Ljava/lang/Object;");
					} else {
						throw new IllegalArgumentException("JSON类型仅能出现一次(反序列化啊,你还要闹哪样)");
					}
					break;
			}
			if ((bodyKind & 6) == 6) throw new IllegalArgumentException("不能同时使用POST_KV和JSON");
			if (name == null) return;

			if (fromSlot == 0) throw new IllegalStateException("不支持的类型/"+from1);

			c.var(ALOAD, fromSlot);
			c.ldc(new CstString(field.getString("value", name)));
			c.invokeItf("java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;");

			if (type.owner != null || type.array() > 0) {
				if (type.owner != null) {
					if (type.owner.equals("java/lang/String") ||
						type.owner.equals("java/lang/CharSequence") ||
						type.owner.equals("java/lang/Object")) {
						cw.clazz(CHECKCAST, type.owner);
						return;
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
				cw.newObject("java/lang/NumberFormatException");
				cw.one(ATHROW);
				cw.label(label);
			} else {
				name = Type.toString(type.type);
				name = Character.toUpperCase(name.charAt(0))+name.substring(1);
				cw.invoke(INVOKESTATIC, "java/lang/"+name, "parse"+(name.equals("Integer")?"Int":name), "(Ljava/lang/String;)"+(char)type.type);
			}
		}

		private static String firstType(Generic generic) {
			return generic.children.get(0).owner();
		}
	}

	private static final class Find extends MutableInt implements BiFunction<MutableInt, Object, Boolean> {
		int action, maxLen;
		ASet result;

		@Override
		public Boolean apply(MutableInt kPos, Object o) {
			ASet v = getASet(o, action);
			if (((1<<action)&v.accepts) != 0) {
				if (v.prefix || kPos.getValue() == maxLen) result = v;
			}
			return false;
		}
	}

	private static final class ASet {
		int accepts;
		Dispatcher req, prec;
		boolean prefix;
	}
	private static ASet getASet(Object o, int action) {
		return o instanceof ASet ? ((ASet) o) : ((ASet[]) o)[action];
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		String path = req.path();
		int len = path.length();
		while (len > 1 && path.charAt(len-1) == '/') len--;

		ASet set;
		if (len == 0) {
			set = getASet(route.get(""), req.action());
		} else {
			Find find = (Find) req.threadLocalCtx().get("or:fn");
			if (find == null) req.threadLocalCtx().put("or:fn", find = new Find());

			find.action = req.action();
			find.maxLen = len;
			find.result = null;

			route.longestWithCallback(path, 0, len, find, find);

			set = find.result;
		}

		if (set == null || ((1 << req.action()) & set.accepts) == 0) return;

		req.ctx().put("or:router", set);
		if (set.prec != null) {
			Object ret = set.prec.invoke(req, req.handler(), cfg);
			if (ret instanceof Response) {
				throw new IllegalRequestException(200, ((Response) ret));
			}
		}
	}

	@Override
	public Response response(Request req, ResponseHeader rh) throws IOException {
		ASet set = (ASet) req.ctx().remove("or:router");
		if (set == null) return StringResponse.httpErr(403);

		Object ret = set.req.invoke(req, rh, null);
		if (ret instanceof Response) return (Response) ret;
		if (ret == null) return null;
		return new StringResponse(ret.toString());
	}

	private static final class LazyAdapter {
		static final SerializerManager ADAPTER_FACTORY = new SerializerManager();
	}

	public static Object _JAdapt(Request req, String type) throws IllegalRequestException {
		if (req.postBuffer() == null) throw new IllegalRequestException(400, "Not post");

		CCJson jsonp = (CCJson) req.threadLocalCtx().get("or:jsonp");
		if (jsonp == null) req.threadLocalCtx().put("or:jsonp", jsonp = new CCJson());

		CAdapter<?> adapter = LazyAdapter.ADAPTER_FACTORY.adapter(Signature.parseGeneric(type));
		try {
			adapter.reset();
			jsonp.parseRaw(req.postBuffer(), adapter, JSONParser.LITERAL_KEY);
			return adapter.result();
		} catch (Exception e) {
			throw new IllegalRequestException(400, "JSON数据无效", e);
		}
	}

	interface Dispatcher {
		Object invoke(Request req, ResponseHeader srv, Object extra);
		Dispatcher copyWith(int methodId, Object ref);
	}
}
