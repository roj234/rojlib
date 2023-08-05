package roj.plugins.mychat;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.serial.ToJson;
import roj.config.word.ITokenizer;
import roj.crypt.HMAC;
import roj.crypt.SM3;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ServerLaunch;
import roj.net.http.HttpUtil;
import roj.net.http.IllegalRequestException;
import roj.net.http.srv.*;
import roj.net.http.srv.autohandled.*;
import roj.net.http.ws.WebsocketHandler;
import roj.net.http.ws.WebsocketManager;
import roj.text.ACalendar;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.text.logging.LoggingStream;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
public class Server extends WebsocketManager implements Router, Context {
	static final SecureRandom rnd = new SecureRandom();
	final byte[] secKey = new byte[16];

	public Server() {
		Set<String> prot = getValidProtocol();
		prot.clear();
		prot.add("WSChat");
		rnd.nextBytes(secKey);
	}

	@Override
	protected WebsocketHandler newWorker(Request req, ResponseHeader handle) {
		ChatImpl w = new ChatImpl();
		w.owner = (User) userMap.get((int) req.threadContext().remove("id"));
		return w;
	}

	static File attDir;
	static ChatDAO dao;
	static IntMap<AbstractUser> userMap = new IntMap<>();

	public static void main(String[] args) throws IOException {
		File path = new File("mychat");
		dao = new ChatDAO(path);

		attDir = new File(path, "att");
		if (!attDir.isDirectory() && !attDir.mkdirs()) {
			throw new IOException("Failed to create attachment directory");
		}

		FileResponse.loadMimeMap(IOUtil.readUTF(new File("mychat/mime.ini")));

		PrintStream out = new LoggingStream();
		System.setOut(out);
		System.setErr(out);

		User S = new User();
		S.name = "系统";
		S.desc = "系统管理账号,其UID为0";
		S.face = "http://127.0.0.1:1999/head/0";

		User A = new User();
		A.id = 1;
		A.name = A.username = "A";
		A.desc = "";
		A.face = "http://127.0.0.1:1999/head/1";
		A.friends.add(2);
		A.joinedGroups.add(1000000);

		User B = new User();
		B.id = 2;
		B.name = B.username = "B";
		B.desc = "";
		B.face = "http://127.0.0.1:1999/head/2";
		B.friends.add(1);
		B.joinedGroups.add(1000000);

		Group T = new Group();
		T.id = 1000000;
		T.name = "测试群聊";
		T.desc = "测试JSON数据\n<I>调试模式已经开启!!</I>\nPowered by Async/2.1";
		T.face = "http://127.0.0.1:1999/head/1000000";
		T.joinGroup(A);
		T.joinGroup(B);

		r(S);
		r(A);
		r(B);
		r(T);

		Server man = new Server();
		ServerLaunch server = HttpServer11.simple(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1999), 233, man);
		man.loop = server.loop();
		server.launch();

		System.out.println("监听 " + server.localAddress());
	}

	static final ZipRouter chatHtml;

	static {
		try {
			chatHtml = new ZipRouter("mychat/chat.zip");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static void r(AbstractUser b) {
		userMap.putInt(b.id, b);
	}

	@Override
	public void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		router.checkHeader(req, cfg);
		if (cfg != null) {
			if (!cfg.postAccepted()) cfg.postAccept(131072, 200);
		}
	}

	private static void jsonErrorPre(Request req, String str) {
		req.handler().die().code(200).headers("Access-Control-Allow-Origin: *").body(jsonErr(str));
	}

	OKRouter router = (OKRouter) new OKRouter().register(this);

	@Override
	public Response response(Request req, ResponseHeader rh) throws Exception {
		rh.headers("Access-Control-Allow-Headers: MCTK\r\n" +
			"Access-Control-Allow-Origin: " + req.getField("Origin"));

		if (HttpUtil.isCORSPreflight(req)) {
			return rh.code(200)
					 .headers("Access-Control-Allow-Headers: MCTK\r\n" +
						 "Access-Control-Allow-Origin: " + req.getField("Origin") + "\r\n" +
						 "Access-Control-Max-Age: 2592000\r\n" +
						 "Access-Control-Allow-Methods: *")
					 .returnNull();
		}

		// Strict-Transport-Security: max-age=1000; includeSubDomains

		return router.response(req, rh);
	}

	private static String pathFilter(String path) {
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (!TextUtil.isPrintableAscii(c) || c == '/' || c == '\\') return "invalid";
		}
		return path;
	}

	@Interceptor
	public String logon(Request req, ResponseHeader rh, PostSetting ps) {
		String token = req.getField("MCTK");

		int i = token.indexOf('-');
		if (i > 0) {
			String userId = token.substring(0, i);
			if (0 == TextUtil.isNumber(userId)) {
				int userIdInt = TextUtil.parseInt(userId);
				User user = dao.getUser(userIdInt);
				if (user != null && verifyToken(user, 1, token.substring(i+1))) {
					return null;
				}
			}
		}

		return rh.code(403).returns("请登录");
	}

	@Interceptor
	public Object parallelLimit(Request req, ResponseHeader rh, PostSetting ps) {
		User u = (User) req.threadContext().get("USER");

		// 每秒5个请求 / 最高4个并行上传|下载
		if (u.parallelUD.get() < 0 || u.attachCounter.sum() > 5) {
			rh.code(503).header("Retry-After", "60");
			return jsonErr("系统繁忙,请稍后再试");
		}

		u.parallelUD.getAndDecrement();
		rh.finishHandler((__) -> {
			u.parallelUD.getAndIncrement();
			return false;
		});

		return null;
	}

	@Route
	public String ping() { return "pong"; }

	@Route(value = "file/", type = Route.Type.PREFIX)
	@Accepts(Accepts.GET)
	@Interceptor({"login","parallelLimit"})
	public Response getFile(Request req, ResponseHeader rh) {
		String safePath = IOUtil.safePath(req.subDirectory(1).path());
		File file = new File(attDir, safePath);
		if (!file.isFile()) return rh.code(404).returnNull();
		return new DiskFileInfo(file).response(req, rh);
	}

	@Route(value = "file/", type = Route.Type.PREFIX)
	@Accepts(Accepts.DELETE)
	@Interceptor("logon")
	public Object deleteFile(Request req) {
		User u = (User) req.threadContext().get("USER");

		String safePath = IOUtil.safePath(req.subDirectory(1).path());
		DynByteBuf bb = IOUtil.SharedCoder.get().decodeBase64R(safePath);
		if (bb.readInt() != u.id) {
			return jsonErr("没有权限");
		} else {
			File file = new File("attachment/" + safePath);
			if (file.isFile() && file.delete()) return "{\"ok\":1}";
			else return jsonErr("删除失败");
		}
	}

	@Interceptor
	public void fileUpload(Request req, ResponseHeader rh, PostSetting ps) {
		User u = (User) req.threadContext().get("USER");

		List<String> restful = req.directories();

		boolean img = restful.get(1).contains("img");
		int count = TextUtil.parseInt(restful.get(2));

		ps.postAccept(img ? 4194304 : 16777216, 10000);
		ps.postHandler(new UploadHandler(req, count, u.id, img));
	}

	@Route(value = "file/", type = Route.Type.PREFIX)
	@Accepts(Accepts.POST)
	@Interceptor({"logon","parallelLimit","fileUpload"})
	public String postFile(Request req) {
		UploadHandler ph = (UploadHandler) req.postHandler();

		ToJson ser = new ToJson();
		ser.valueList();

		File[] files = ph.files;
		String[] errors = ph.errors;
		for (int i = 0; i < files.length; i++) {
			ser.valueMap();

			File f = files[i];
			boolean ok = errors == null || errors[i] == null;
			if (ok) files[i] = null;

			ser.key("ok");
			ser.value(ok);

			ser.key("v");
			ser.value(ok ? f.getName() : errors[i]);

			ser.pop();
		}
		return ser.getValue().toStringAndFree();
	}

	@Route
	@Interceptor("logon")
	@Accepts(Accepts.GET)
	public Response im(Request req, ResponseHeader rh) { return switchToWebsocket(req, rh); }

	// region user : register login head_image logout set_info
	@Route
	@Accepts(Accepts.POST)
	@Body(From.POST_KV)
	public Response user__register(String name, String pass) {
		if (name == null || pass == null) return jsonErr("缺参数");

		ChatDAO.Result rs = dao.register(name, pass);
		if (rs.error == null) {
			return new StringResponse("{\"ok\":1}");
		} else {
			return jsonErr(rs.error);
		}
	}

	@Route
	@Accepts(Accepts.POST)
	@Body(From.POST_KV)
	public Object user__login(Request req, String name, String hash, String salt) {
		if (name == null || hash == null || salt == null) return jsonErr("缺参数");
		if (!verifyToken(null, 1, salt)) return "";

		User u = dao.getUserByName(name);
		if (!u.exist()) return jsonErr("用户名或密码错误");
		if (System.currentTimeMillis() < u.timeout) return jsonErr("密码错误次数过多,请在"+ACalendar.toLocalTimeString(u.timeout)+"后重试");

		HMAC hmac = (HMAC) initLocal().get("HASH");
		hmac.setSignKey(IOUtil.SharedCoder.get().decodeBase64(salt));
		hmac.update(u.pass);

		int eq = 0;
		ByteList in = IOUtil.SharedCoder.get().decodeBase64R(hash);
		for (byte digest : hmac.digestShared())
			eq |= in.get()^digest;

		if (eq != 0) {
			// >= : async
			if (++u.passError >= 3) u.timeout = System.currentTimeMillis() + 60000;
			return jsonErr("用户名或密码错误");
		}

		synchronized (u) {
			if (u.worker != null) {
				u.worker.sendExternalLogout(
					"您已在他处登录<br />" +
						"IP: " + req.handler().ch.remoteAddress() + "<br />" +
						"UA: " + req.getField("User-Agent") + "<br />" +
						"时间: " + ACalendar.toLocalTimeString(System.currentTimeMillis()) + "<br />" +
						"密码错误次数: " + u.passError);
			}

			u.passError = 0;
			u.timeout = 0;
			rnd.nextBytes(u.tokenSalt);

			CMapping m = new CMapping();
			m.put("user", u.put());
			m.put("protocol", "WSChat");
			m.put("address", "ws://127.0.0.1:1999/im/");
			m.put("token", createToken(u, -1, 86400000));
			m.put("ok", true);
			return new StringResponse(m.toShortJSONb());
		}
	}

	@Route
	@Accepts(Accepts.GET)
	public String user__challenge() { return createToken(null, 1, 300000); }

	@Route
	@Accepts(Accepts.GET)
	public Response user__head(Request req, ResponseHeader rh) {
		File img = new File(attDir, pathFilter(req.path().substring(9)));
		if (!img.isFile()) img = new File(attDir, "head_default");

		rh.headers("Access-Control-Allow-Origin: *");
		return new DiskFileInfo(img).response(req, rh);
	}

	@Route
	@Interceptor("logon")
	@Accepts(Accepts.GET)
	public String user__logout(Request req) {
		User u = (User) req.threadContext().get("USER");

		u.tokenSalt[0]++;
		try {
			if (u.worker != null) u.worker.ch.close();
		} catch (Throwable ignored) {}

		return "{\"ok\":1}";
	}

	@Route
	@Interceptor("logon")
	@Accepts(Accepts.POST)
	public Object user__set_info(Request req) throws IllegalRequestException {
		User u = (User) req.threadContext().get("USER");

		Map<String, String> x = req.postFields();
		String newpass = x.getOrDefault("newpass", Helpers.cast(""));
		ChatDAO.Result rs = dao.changePassword(u.id, x.get("pass"), newpass.isEmpty() ? null : newpass);
		if (rs.error != null) return jsonErr(rs.error);

		/*ByteList face = (ByteList) x.get("face");
		if (face != null) {
			try {
				BufferedImage image = ImageIO.read(face.asInputStream());
				if (image != null) ImageIO.write(image, "PNG", new File(headDir, Integer.toString(uid)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}*/
		if (!x.containsKey("flag") || !x.containsKey("desc") || !x.containsKey("name")) return jsonErr("缺数据!");

		u.username = x.get("name");
		u.desc = x.get("desc");
		u.flag2 = TextUtil.parseInt(x.get("flag"));
		u.onDataChanged(this);

		rs = dao.setUserData(u);
		return rs.error == null ? new StringResponse("{\"ok\":1}") : jsonErr(rs.error);
	}
	// endregion

	@Route
	@Interceptor("logon")
	@Accepts(Accepts.GET)
	@Body(From.POST_KV)
	public Object space(Request req, String off, String len) throws IllegalRequestException {
		User u = (User) req.threadContext().get("USER");

		int[] num = (int[]) req.threadContext().get("IA");
		num[0] = 10;

		int uid1;
		List<String> restful = req.directories();
		if (restful.size() > 1) {
			if (!TextUtil.parseIntOptional(restful.get(1), num)) return jsonErr("参数错误");
			uid1 = num[0];
		} else {
			uid1 = -1;
		}

		num[0] = 10;
		if (!TextUtil.parseIntOptional(off, num)) return jsonErr("参数错误");
		int off1 = num[0];

		num[0] = 10;
		if (!TextUtil.parseIntOptional(len, num)) return jsonErr("参数错误");
		int len1 = num[0];

		req.handler().chunked();
		// todo DBA
		return "not impl";
	}

	@Route
	@Interceptor("logon")
	@Accepts(Accepts.POST)
	@Body(From.POST_KV)
	public String  space__add(Request req, String text) {
		User u = (User) req.threadContext().get("USER");

		SpaceEntry entry = new SpaceEntry();
		entry.text = text;
		entry.time = System.currentTimeMillis();
		entry.uid = u.id;
		entry.id = 1;


		return "{\"ok\":1}";
	}

	@Route
	@Interceptor("logon")
	@Accepts(Accepts.POST)
	public Object space__del(Request req, String id) {
		User u = (User) req.threadContext().get("USER");

		return jsonErr("未实现");
	}

	private Response friendOp(Request request, List<String> lst) {
		switch (lst.get(1)) {
			case "search":
				// text: 内容
				// type: 内容类型 UID 名称
				// flag: online=1 group=2 person=3
			case "add":
				// id: id
				// text: 验证消息
			case "remove":
				// id: id
			case "move":
				// id: id
				// group: 移动到的分组
			case "confirm":
				// 确认添加好友
				// id: id
			case "flag":
				// id: id
				// flag: 安全标志位
				// (服务端)online=1
				// no_see_him=2, no_see_me=3, blocked=4, always_offline=5
				return new StringResponse("{\"ok\":0,\"err\":\"EmbeddedWSChat不提供好友列表\"}");
			case "list":
				ToJson ser = new ToJson();
				CList list = new CList();
				CMapping map = new CMapping();
				map.getOrCreateList("测试分组").add(userMap.get(0).put()).add(userMap.get(1).put());
				list.add(map);
				list.add(new CList());
				list.add(new CList().add(userMap.get(1000000).put()));
				return new StringResponse(list.toShortJSONb());
			default:
				return null;
		}
	}

	// region Misc

	private static Response jsonErr(String s) {
		return new StringResponse("{\"ok\":0,\"err\":\"" + ITokenizer.addSlashes(s) + "\"}", "application/json");
	}

	private static final AtomicLong seqNum = new AtomicLong();
	private boolean verifyToken(User u, int action, String token) {
		ByteList bb = IOUtil.SharedCoder.get().decodeBase64R(token);
		// action not same
		if ((action & bb.readInt()) == 0) return false;
		// expired
		if (System.currentTimeMillis() > bb.readLong()) return false;
		// 重放攻击
		if ((u == null ? seqNum.get() : u.tokenSeq) > bb.readLong()) return false;

		HMAC hmac = (HMAC) initLocal().get("HASH");
		hmac.setSignKey(u == null ? secKey : u.tokenSalt);
		hmac.update(bb.list, 0, bb.wIndex());

		int eq = 0;
		for (byte digest : hmac.digestShared())
			eq |= digest ^ bb.get();
		return eq == 0;
	}
	// usage | timestamp | seqNum | hash
	private String createToken(User u, int usage, long expire) {
		UTFCoder uc = IOUtil.SharedCoder.get();
		ByteList bb = uc.byteBuf; bb.clear();
		bb.putInt(usage).putLong(expire < 0 ? -expire : System.currentTimeMillis() + expire).putLong(u == null ? seqNum.getAndIncrement() : u.tokenSeq++);

		HMAC hmac = (HMAC) initLocal().get("HASH");
		hmac.setSignKey(u == null ? secKey : u.tokenSalt);
		hmac.update(bb.list, 0, bb.wIndex());
		return uc.encodeBase64(bb.put(hmac.digestShared()));
	}

	private static MyHashMap<String, Object> initLocal() {
		MyHashMap<String, Object> L = HttpServer11.TSO.get().ctx;
		if (!L.containsKey("LST")) {
			L.put("HASH", new HMAC(new SM3()));
			L.put("IA", new int[1]);
			L.put("LST", new SimpleList<>());
		}
		return L;
	}

	// endregion

	static class ChatImpl extends WSChat {
		static Context get = userMap::get;

		public User owner;

		@Override
		protected void init() {
			owner.onLogon(get, this);
			AbstractUser g = userMap.get(1000000);

			sendMessage(g, new Message(Message.STYLE_SUCCESS, "欢迎使用MyChat2!"), true);
			sendMessage(g, new Message(Message.STYLE_WARNING | Message.STYLE_BAR, "欢迎使用MyChat2!"), true);
			sendMessage(g, new Message(Message.STYLE_ERROR, "欢迎使用MyChat2!"), true);
			sendMessage(g, new Message(0,
									   "欢迎使用MyChat2 (下称\"软件\")\n" + "本软件由Roj234独立开发并依法享有其知识产权\n\n" + "软件以MIT协议开源,并\"按原样提供\", 不包含任何显式或隐式的担保,\n" + "上述担保包括但不限于可销售性, 对于特定情况的适应性和安全性\n" + "无论何时，无论是否与软件有直接关联, 无论是否在合同或判决等书面文件中写明\n" + "作者与版权拥有者都不为软件造成的直接或间接损失负责\n\n" + "[c:red]如不同意本协议, 请勿使用本软件的任何服务[/c]"),
						false);
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			super.channelClosed(ctx);
			owner.onLogout(get, this);
		}

		@Override
		protected void message(int to, CharSequence msg) {
			AbstractUser u = userMap.get(to);
			if (u == null) {
				sendExternalLogout("运行时错误: 不存在的用户 " + to);
				return;
			}
			u.postMessage(get, new Message(owner.id, msg.toString()), false);
		}

		@Override
		protected void requestUserInfo(int id) {
			AbstractUser u = userMap.get(id);
			if (u == null) {
				sendExternalLogout("运行时错误: 不存在的用户 " + id);
				return;
			}
			sendUserInfo(u);
		}

		@Override
		protected void requestHistory(int id, CharSequence filter, int off, int len) {
			AbstractUser u = userMap.get(id);
			if (u instanceof Group) {
				Group g = (Group) u;
				RingBuffer<Message> his = g.history;

				if (len == 0) {
					sendHistory(id, his.size(), Collections.emptyList());
					return;
				} else if (len > 100) len = 100;

				SimpleList<Message> msgs = new SimpleList<>(Math.min(his.capacity(), len));

				off = his.size() - len - off;
				if (off < 0) {
					len += off;
					off = 0;
				}
				his.getSome(1, his.head(), his.tail(), msgs, off, len);

				filter = filter.toString();
				if (filter.length() > 0) {
					for (int i = msgs.size() - 1; i >= 0; i--) {
						if (!msgs.get(i).text.contains(filter)) {
							msgs.remove(i);
						}
					}
				}
				sendHistory(id, his.size(), msgs);
			} else {
				if (len == 0) {
					sendHistory(id, dao.getHistoryCount(id), Collections.emptyList());
					return;
				}

				MutableInt mi = new MutableInt();
				List<Message> msg = dao.getHistory(id, filter, off, len, mi);
				sendHistory(id, mi.getValue(), msg);
			}
		}

		@Override
		protected void requestClearHistory(int id, int timeout) {
			AbstractUser u = userMap.get(id);
			if (!(u instanceof User)) return;

			ChatDAO.Result r = dao.delHistory(owner.id, id);
			if (r.error != null) {
				sendAlert("无法清除历史纪录: " + r.error);
			}
		}

		@Override
		protected void requestColdHistory(int id) {
			AbstractUser u = userMap.get(id);
			if (!(u instanceof User)) return;

			System.out.println("请求冷却历史纪录 " + id);
		}
	}

	@Override
	public AbstractUser getUser(int id) {
		return dao.getUser(id);
	}
}