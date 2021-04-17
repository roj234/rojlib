package roj.net.mychat;

import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.concurrent.DualBuffered;
import roj.concurrent.TaskPool;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.config.serial.ToJson;
import roj.config.word.ITokenizer;
import roj.crypt.Base64;
import roj.crypt.SM3;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.math.MutableInt;
import roj.net.ch.ChannelCtx;
import roj.net.ch.osi.ServerLaunch;
import roj.net.http.Action;
import roj.net.http.Headers;
import roj.net.http.HttpUtil;
import roj.net.http.IllegalRequestException;
import roj.net.http.srv.*;
import roj.net.http.ws.WebsocketHandler;
import roj.net.http.ws.WebsocketManager;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.text.logging.LoggingStream;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.*;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
public class Server extends WebsocketManager implements Router, Context {
	static final SecureRandom rnd = new SecureRandom();
	byte[] secKey;

	public Server() {
		Set<String> prot = getValidProtocol();
		prot.clear();
		prot.add("WSChat");
		secKey = new byte[16];
		rnd.nextBytes(secKey);
	}

	@Override
	protected WebsocketHandler newWorker(Request req, ResponseHeader handle) {
		ChatImpl w = new ChatImpl();
		w.owner = (User) userMap.get((int) req.threadContext().remove("id"));
		return w;
	}

	static File headDir, attDir, imgDir;
	static ChatDAO dao;
	static IntMap<AbstractUser> userMap = new IntMap<>();

	static final TaskPool POOL = new TaskPool(1, 4, 1, 60000, "Pool1-");

	static TimeCounter sharedCounter = new TimeCounter(60000);

	public static void main(String[] args) throws IOException {
		File path = new File("mychat");
		dao = new ChatDAO(path);

		headDir = new File(path, "head");
		if (!headDir.isDirectory() && !headDir.mkdirs()) {
			throw new IOException("Failed to create head directory");
		}

		attDir = new File(path, "att");
		if (!attDir.isDirectory() && !attDir.mkdirs()) {
			throw new IOException("Failed to create attachment directory");
		}

		imgDir = new File(path, "img");
		if (!imgDir.isDirectory() && !imgDir.mkdirs()) {
			throw new IOException("Failed to create image directory");
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
		man.loop = server.getLoop();
		server.launch();

		System.out.println("监听 " + server.address());
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
		if (cfg != null) {
			cfg.postAccept(postMaxLength(req), 1000);
		}
	}

	public long postMaxLength(Request req) {
		switch (req.path()) {
			case "/user/login":
			case "/user/reg":
			case "/user/captcha":
				return 512;
		}
		int uid = verifyHash(req);
		if (uid < 0) {
			jsonErrorPre(req, "请登录");
			return 0;
		}

		if (req.path().startsWith("/att/upload/") || req.path().startsWith("/img/upload/")) {
			User u = (User) userMap.get(uid);
			if (u.largeConn.incrementAndGet() > 4) {
				u.largeConn.decrementAndGet();
				jsonErrorPre(req, "系统繁忙");
				return 0;
			}

			int fileCount = TextUtil.parseInt(req.path().substring(12));
			boolean img = req.path().startsWith("/img/");
			req.handler().postHandler(new UploadHandler(req, fileCount, uid, img));
			return img ? 4194304 : 16777216;
		}

		switch (req.path()) {
			case "/user/set_info":
				return 131072;
			case "/space/add":
				return 65536;
			case "/friend/add":
				return 1024;
			default:
				return 128;
		}
	}

	private static void jsonErrorPre(Request req, String str) {
		req.handler().die().code(200).headers("Access-Control-Allow-Origin: *").body(jsonErr(str));
	}

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

		// IM
		if ("websocket".equals(req.getField("Upgrade"))) {
			int id = verifyHash(req.path().substring(1), rh);
			if (id < 0) return rh.code(403).returnNull();

			req.threadContext().put("id", id);
			return switchToWebsocket(req, rh);
		}

		List<String> lst = getRestfulUrl(req);
		if (lst.isEmpty()) return rh.code(403).returnNull();

		int uid = verifyHash(req);

		Response v = null;
		switch (lst.get(0)) {
			case "":
				return new StringResponse("MyChat Server");
			case "friend":
				if (lst.size() < 2) break;
				if (uid < 0) break;

				v = friendOp(req, lst);
				break;
			case "user":
				if (lst.size() < 2) break;

				v = userOp(req, lst, uid);
				break;
			case "space":
				if (lst.size() < 2) break;

				v = spaceOp(req, lst, uid);
				break;
			case "ping":
				v = new StringResponse("pong");
				break;
			// standard image
			case "img": {
				if (lst.size() < 2) break;

				User u = (User) userMap.get(uid);

				if (lst.get(1).equals("upload")) {
					u.largeConn.decrementAndGet();
					v = doUpload(req);
					break;
				}

				TimeCounter tc = u == null ? sharedCounter : u.imgCounter;

				// 每秒10个请求
				if (tc.plus() > 10) {
					v = jsonErr("系统繁忙");
					break;
				}

				File file = new File(imgDir, pathFilter(req.path().substring(5)));
				if (!file.isFile()) return rh.code(404).returns(StringResponse.httpErr(404));

				v = new HttpFile(file).response(req, rh);
				//使用 'Vary: Origin' 告知浏览器根据Origin的变化重新匹配缓存
				rh.headers("Access-Control-Allow-Origin: *");
				return v;
			}
			// head image
			case "head": {
				File img = new File(headDir, pathFilter(req.path().substring(6)));
				if (!img.isFile()) img = new File(headDir, "default");
				if (!img.isFile()) throw new IllegalArgumentException("错误: 没有默认头像");
				v = new HttpFile(img).response(req, rh);
				rh.headers("Access-Control-Allow-Origin: *");
				return v;
			}
			// attachment
			case "att": {
				if (lst.size() < 2) break;

				User u = (User) userMap.get(uid);

				if (lst.get(1).equals("upload")) {
					if (u == null) break;
					u.largeConn.decrementAndGet();
					v = doUpload(req);
					break;
				} else if (lst.get(1).equals("token")) {
					if (u == null) break;
					// bit2: attachment, expire: 30 minutes
					return new StringResponse("{\"ok\":1,\"code\":\"" + token(u, 1, 180000) + "\"}");
				}

				if (lst.size() < 4) break;

				// /att/[attachment name]/[expire time]/[hash]

				File file = new File(attDir, lst.get(1));
				if (!file.isFile()) return rh.code(404).returns(StringResponse.httpErr(404));

				if (req.action() == Action.DELETE) {
					UTFCoder uc = IOUtil.SharedCoder.get();
					ByteList bb = uc.decodeBase64R(file.getName());
					if (bb.wIndex() < 4 || bb.readInt(bb.wIndex() - 4) != uid) {v = jsonErr("没有权限");} else {
						int i = 10;
						while (!file.delete()) {
							if (i-- == 0) {
								v = jsonErr("删除失败");
								break;
							}
						}
						if (v == null) v = new StringResponse("{\"ok\":1}");
					}
					break;
				}

				// 每秒5个请求 / 最高4个分块
				if (u.largeConn.get() > 4 || (u.attachCounter.plus() > 5 && file.length() > 2048)) {
					v = jsonErr("系统繁忙,请稍后再试");
					//Retry-After: 120
					break;
				}
				u.largeConn.getAndIncrement();
				req.handler().finishHandler((rh1) -> {
					u.largeConn.decrementAndGet();
					return false;
				});
				v = new HttpFile(file).response(req, rh);
				if (!req.getField("Origin").isEmpty()) rh.headers("Access-Control-Allow-Origin: *");
				return v;
			}
			case "html":
				req.subDirectory(1);
				return chatHtml.response(req, rh);
		}

		if (v != null) {
			if (!req.getField("Origin").isEmpty()) rh.code(200).headers("Access-Control-Allow-Origin: *");
			return v;
		}

		return rh.code(403).returns(StringResponse.httpErr(403));
	}

	private static String pathFilter(String path) {
		for (int i = 0; i < path.length(); i++) {
			char c = path.charAt(i);
			if (!TextUtil.isPrintableAscii(c) || c == '/' || c == '\\') return "invalid";
		}
		return path;
	}

	private static Response doUpload(Request req) {
		UploadHandler ph = (UploadHandler) req.postHandler();
		if (ph == null) return null;

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
		return new StringResponse(ser.getValue());
	}

	private Response userOp(Request req, List<String> lst, int uid) throws Exception {
		switch (lst.get(1)) {
			case "info":
				if (uid < 0) return jsonErr("请登录");

				CMapping m = new CMapping();
				m.put("user", userMap.get(uid).put());
				m.put("protocol", "WSChat");
				m.put("address", "ws://127.0.0.1:1999/" + req.getField("MCTK"));
				m.put("ok", 1);
				return new StringResponse(m.toShortJSONb());
			case "set_info":
				if (uid < 0) return jsonErr("请登录");

				Map<String, ?> x = req.postFields();
				String newpass = x.getOrDefault("newpass", Helpers.cast("")).toString();
				ChatDAO.Result rs = dao.changePassword(uid, x.get("pass").toString(), newpass.isEmpty() ? null : newpass);
				if (rs.error != null) return jsonErr(rs.error);

				ByteList face = (ByteList) x.get("face");
				if (face != null) {
					try {
						BufferedImage image = ImageIO.read(face.asInputStream());
						if (image != null) ImageIO.write(image, "PNG", new File(headDir, Integer.toString(uid)));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				if (!x.containsKey("flag") || !x.containsKey("desc") || !x.containsKey("name")) return jsonErr("缺数据!");
				User u = (User) userMap.get(uid);
				u.username = x.get("name").toString();
				u.desc = x.get("desc").toString();
				u.flag2 = TextUtil.parseInt(x.get("flag").toString());
				u.onDataChanged(this);

				rs = dao.setUserData(u);
				return rs.error == null ? new StringResponse("{\"ok\":1}") : jsonErr(rs.error);
			case "code":
				return new StringResponse("{\"ok\":1,\"code\":\"" + token(null, 1, 300000) + "\"}");
			case "login":
				Map<String, String> posts = req.postFields();
				if (posts == null) return jsonErr("数据错误");

				String name = posts.get("name");
				String pass = posts.get("pass");
				String challenge = posts.get("chag");
				String code = posts.get("code");
				rs = dao.login(name, pass, challenge);
				if (rs.error != null) {
					return jsonErr(rs.error);
				}
				u = (User) userMap.get(rs.uid);

				if (u.worker != null) {
					u.worker.sendExternalLogout("您已在异地登录, 如果这不是您的操作...<br />" + "但是我们没有开发账号冻结机制... <br />" + "目的IP地址: " + req.handler().ch.remoteAddress() + "<br />" + "UA: " + req.getField(
						"User-Agent") + "<br />" + "时间: " + new ACalendar().formatDate("Y-m-d H:i:s.x", System.currentTimeMillis()));
				}

				rnd.nextBytes(u.salt);
				String hash = hash(u, req.handler());
				return new StringResponse("{\"ok\":1,\"token\":\"" + rs.uid + '-' + hash + "\"}");
			case "logout":
				if (uid >= 0) {
					u = (User) userMap.get(uid);
					u.salt[0]++;
					try {
						if (u.worker != null) u.worker.ch.close();
					} catch (Throwable ignored) {}
				}
				return new StringResponse("{\"ok\":1}");
			case "reg":
				posts = req.postFields();
				if (posts == null) return jsonErr("数据错误");

				name = posts.get("name");
				pass = posts.get("pass");
				rs = dao.register(name, pass);
				if (rs.error == null) {
					return new StringResponse("{\"ok\":1}");
				} else {
					return jsonErr(rs.error);
				}
			default:
				return null;
		}
	}

	private final DualBuffered<List<SpaceEntry>, RingBuffer<SpaceEntry>> spaceAtThisTime = new DualBuffered<List<SpaceEntry>, RingBuffer<SpaceEntry>>(new SimpleList<>(), new RingBuffer<>(100, false)) {
		@Override
		protected void move() {
			List<SpaceEntry> w = this.w;
			RingBuffer<SpaceEntry> r = this.r;

			for (int i = 0; i < w.size(); i++) {
				r.ringAddLast(w.get(i));
			}
			w.clear();
		}
	};

	private Response spaceOp(Request req, List<String> lst, int uid) throws IOException {
		Map<String, String> $_REQUEST = req.fields();
		switch (lst.get(1)) {
			case "list":
				int[] num = (int[]) req.threadContext().get("IA");
				num[0] = 10;

				int uid1;
				if (lst.size() > 2) {
					if (!TextUtil.parseIntOptional(lst.get(2), num)) return jsonErr("参数错误");
					uid1 = num[0];
				} else {
					uid1 = -1;
				}

				num[0] = 10;
				if (!TextUtil.parseIntOptional($_REQUEST.get("off"), num)) return jsonErr("参数错误");
				int off1 = num[0];

				num[0] = 10;
				if (!TextUtil.parseIntOptional($_REQUEST.get("len"), num)) return jsonErr("参数错误");
				int len1 = num[0];

				req.handler().chunked();
				return new ChunkedSpaceResp(off1, len1, uid1);
			case "add":
				if (uid < 0) return jsonErr("请登录");

				SpaceEntry entry = new SpaceEntry();
				entry.text = $_REQUEST.get("text");
				entry.time = System.currentTimeMillis();
				entry.uid = uid;
				entry.id = 1;

				List<SpaceEntry> entries = spaceAtThisTime.forWrite();
				entries.add(entry);
				spaceAtThisTime.writeFinish();

				return new StringResponse("{\"ok\":1}");
			case "del":
				// post: id
				return jsonErr("暂不支持");
			default:
				return null;
		}
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

	private boolean verifyToken(User u, int action, String token) {
		UTFCoder uc = IOUtil.SharedCoder.get();
		ByteList bb = uc.decodeBase64R(token);
		// action not same
		if ((action & bb.readInt()) == 0) return false;
		// expired
		if (System.currentTimeMillis() > bb.readLong()) return false;

		SM3 sm3 = (SM3) initLocal().get("SM3");
		sm3.reset();

		byte[] secKey = u == null ? this.secKey : u.salt;
		byte[] d0 = bb.list;
		for (int i = 0; i < 10; i++) {
			sm3.update(secKey);
			sm3.update(d0, 0, 20);
		}

		int ne = 0;
		byte[] d1 = sm3.digest();
		for (int i = 0; i < 32; i++) {
			ne |= d1[i] ^ d0[20 + i];
		}
		return ne == 0;
	}

	// action(usage) | timestamp | random | hash
	private String token(User u, int action, int expire) {
		UTFCoder uc = IOUtil.SharedCoder.get();
		ByteList bb = uc.byteBuf;
		bb.clear();
		bb.putInt(action).putLong(expire < 0 ? -expire : System.currentTimeMillis() + expire).putLong(rnd.nextLong());

		SM3 sm3 = (SM3) initLocal().get("SM3");
		sm3.reset();

		byte[] secKey = u == null ? this.secKey : u.salt;
		for (int i = 0; i < 10; i++) {
			sm3.update(secKey);
			sm3.update(bb.list, 0, 20);
		}

		return uc.encodeBase64(bb.put(sm3.digest()));
	}

	private static int verifyHash(Request req) {
		return verifyHash(req.getField("MCTK"), req.handler());
	}

	private static int verifyHash(String hash, ResponseHeader rh) {
		if (hash.isEmpty()) return -1;
		int i = hash.indexOf('-');
		Map<String, Object> L = initLocal();

		int[] num = (int[]) L.get("IA");
		num[0] = 10;

		return i > 0 && TextUtil.parseIntOptional(hash.substring(0, i), num) && userMap.get(num[0]) instanceof User && TextUtil.safeEquals(hash((User) userMap.get(num[0]), rh),
																																			hash.substring(i + 1)) ? num[0] : -1;
	}

	private static String hash(User u, ResponseHeader rh) {
		UTFCoder uc = IOUtil.SharedCoder.get();

		Map<String, Object> L = initLocal();

		SM3 sm3 = (SM3) L.get("SM3");
		sm3.reset();

		sm3.update(u.salt);

		ByteList b = uc.encodeR(u.name);
		sm3.update(b.list, 0, b.wIndex());

		sm3.update(u.salt);

		return uc.encodeBase64(uc.wrap(sm3.digest()), Base64.B64_URL_SAFE);
	}

	private static String challengeCode() {
		return "";
	}

	private static MyHashMap<String, Object> initLocal() {
		MyHashMap<String, Object> L = HttpServer11.TSO.get().ctx;
		if (!L.containsKey("LST")) {
			L.put("SM3", new SM3());
			L.put("IA", new int[1]);
			L.put("LST", new SimpleList<>());
		}
		return L;
	}

	private static List<String> getRestfulUrl(Request req) {
		Map<String, Object> ctx = initLocal();

		List<String> lst = Helpers.cast(ctx.get("LST"));
		lst.clear();

		TextUtil.split(lst, req.path().substring(1), '/', 10, true);
		return lst;
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
		return userMap.get(id);
	}

	private class ChunkedSpaceResp implements Response {
		ToJson ser;
		DynByteBuf tmp;
		Iterator<SpaceEntry> itr;

		public ChunkedSpaceResp(int off1, int len1, int uid1) {
			ser = new ToJson();
			off = off1;
			len = len1;
			uid = uid1;
		}

		@Override
		public void prepare(ResponseHeader srv, Headers h) {
			BufferPool bp = srv.ch().alloc();
			ser.reset();
			ser.valueList();
			tmp = bp.buffer(true, 1024);
			itr = spaceAtThisTime.forRead().descendingIterator();
		}

		@Override
		public boolean send(ResponseWriter rh) throws IOException {
			if (rh.ch().isPendingSend()) {
				rh.ch().flush();
				return true;
			}

			while (itr.hasNext()) {
				SpaceEntry entry = itr.next();
				if (uid >= 0 && entry.uid != uid) continue;
				if (off-- > 0) continue;
				if (len-- == 0) break;

				entry.serialize(ser);

				CharList sb = ser.getHalfValue();
				if (writeString(rh, sb)) return true;
			}

			if (writeString(rh, ser.getValue())) return true;
			rh.write(tmp);
			return tmp.isReadable();
		}

		private boolean writeString(ResponseWriter rh, CharList sb) throws IOException {
			int len = DynByteBuf.byteCountUTF8(sb);
			if (len > tmp.writableBytes()) {
				rh.write(tmp);
				tmp.clear();
				if (rh.ch().isPendingSend()) return true;
			}

			if (len > tmp.capacity())
				tmp = rh.ch().alloc().expand(tmp, len-tmp.capacity());

			tmp.putUTFData(sb);
			sb.clear();
			return false;
		}

		@Override
		public void release(ChannelCtx ctx) throws IOException {
			spaceAtThisTime.readFinish();
			ctx.reserve(tmp);
		}

		int off, len, uid;
	}
}