package roj.plugins.mychat;

import org.jetbrains.annotations.Nullable;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.RingBuffer;
import roj.collect.SimpleList;
import roj.config.ConfigMaster;
import roj.config.Tokenizer;
import roj.config.data.CMap;
import roj.config.serial.ToJson;
import roj.crypt.ILCrypto;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ServerLaunch;
import roj.net.http.HttpUtil;
import roj.net.http.IllegalRequestException;
import roj.net.http.server.*;
import roj.net.http.server.auto.Accepts;
import roj.net.http.server.auto.Interceptor;
import roj.net.http.server.auto.OKRouter;
import roj.net.http.server.auto.Route;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.DynByteBuf;
import roj.util.HighResolutionTimer;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
public class Server implements Router, Context {
	static final SecureRandom rnd = new SecureRandom();
	final byte[] secKey = new byte[16];

	static final Set<String> PROTOCOLS = Collections.singleton("WSChar2");
	public Server() {rnd.nextBytes(secKey);}

	static File attDir;
	static IntMap<AbstractUser> userMap = new IntMap<>();

	public static void main(String[] args) throws IOException {
		File path = new File("plugins/mychat");

		attDir = new File(path, "att");
		if (!attDir.isDirectory() && !attDir.mkdirs()) {
			throw new IOException("Failed to create attachment directory");
		}

		MimeType.loadMimeMap(IOUtil.readUTF(new File("plugins/mychat/mime.ini")));

		User S = new User();
		S.name = "系统";
		S.desc = "系统管理账号,其UID为0";
		S.face = "http://127.0.0.1:1999/user/head/0";

		User A = new User();
		A.id = 1;
		A.name = "A";
		A.desc = "";
		A.face = "http://127.0.0.1:1999/user/head/1";

		User B = new User();
		B.id = 2;
		B.name = "B";
		B.desc = "";
		B.face = "http://127.0.0.1:1999/user/head/2";

		Group T = new Group();
		T.id = 1000000;
		T.name = "测试群聊";
		T.desc = "测试JSON数据\n<I>调试模式已经开启!!</I>\nPowered by Async/2.1";
		T.face = "http://127.0.0.1:1999/user/head/1000000";
		T.joinGroup(A);
		T.joinGroup(B);

		r(S);
		r(A);
		r(B);
		r(T);

		Server man = new Server();
		ServerLaunch server = HttpServer11.simple(new InetSocketAddress(InetAddress.getLoopbackAddress(), 1999), 233, man);
		server.launch();
		man.router.addPrefixDelegation("chat", chatHtml);

		System.out.println("监听 " + server.localAddress());
		HighResolutionTimer.activate();
	}

	static ZipRouter chatHtml;

	static {
		try {
			chatHtml = new ZipRouter("plugins/mychat/chat.zip");
		} catch (Exception e) {
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
		req.server().headers().putAllS("Access-Control-Allow-Headers: MCTK\r\n" +
			"Access-Control-Allow-Origin: " + req.getOrDefault("Origin", "*") + "\r\n" +
			"Access-Control-Max-Age: 2592000\r\n" +
			"Access-Control-Allow-Methods: *");
	}

	private static void jsonErrorPre(Request req, String str) throws IOException {
		req.server().die().code(200).header("Access-Control-Allow-Origin", "*").body(jsonErr(str));
	}

	OKRouter router = new OKRouter().register(this);


	@Override
	public Response response(Request req, ResponseHeader rh) throws Exception {
		if (HttpUtil.isCORSPreflight(req)) {
			return rh.code(204).returnNull();
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
		return null;
	}

	@Interceptor
	public Object parallelLimit(Request req, ResponseHeader rh, PostSetting ps) {
		User u = (User) req.localCtx().get("USER");

		if (u.parallelUD.get() < 0) {
			rh.code(503).header("Retry-After", "60");
			return jsonErr("系统繁忙,请稍后再试");
		}

		u.parallelUD.getAndDecrement();
		rh.onFinish((__) -> {
			u.parallelUD.getAndIncrement();
			return false;
		});

		return null;
	}

	@Route
	public String ping() { return "pong"; }

	@Route(value = "file/", prefix = true)
	@Accepts(Accepts.GET)
	@Interceptor({"logon","parallelLimit"})
	public Response getFile(Request req, ResponseHeader rh) {
		String safePath = IOUtil.safePath(req.subDirectory(1).path());
		File file = new File(attDir, safePath);
		if (!file.isFile()) return rh.code(404).returnNull();
		DiskFileInfo info = new DiskFileInfo(file);
		return Response.file(req, info);
	}

	@Route(value = "file/", prefix = true)
	@Accepts(Accepts.DELETE)
	@Interceptor("logon")
	public Object deleteFile(Request req) {
		User u = (User) req.localCtx().get("USER");

		String safePath = IOUtil.safePath(req.subDirectory(1).path());
		DynByteBuf bb = IOUtil.SharedCoder.get().decodeBase64(safePath);
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
		User u = (User) req.localCtx().get("USER");

		List<String> restful = req.directories();

		boolean img = restful.get(1).contains("img");
		int count = TextUtil.parseInt(restful.get(2));

		ps.postAccept(4194304, 10000);
		ps.postHandler(new UploadHandler(req, count, u.id, img));
	}

	@Route(value = "file/", prefix = true)
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
	@Accepts(Accepts.GET)
	public Response im(Request req) {
		return Response.websocket(req, req1 -> {
			ChatImpl w = new ChatImpl();
			w.owner = (User) userMap.get(1);
			return w;
		}, PROTOCOLS);
	}

	@Route
	@Accepts(Accepts.GET)
	public Response user__info(Request req) {
		// TODO read public key then generate

		User u = (User) getUser(1);
		synchronized (u) {
			if (u.worker != null) {
				u.worker.sendExternalLogout(
					"您已在他处登录<br />" +
						"IP: " + req.connection().remoteAddress() + "<br />" +
						"UA: " + req.getField("User-Agent") + "<br />" +
						"时间: " + ACalendar.toLocalTimeString(System.currentTimeMillis()));
			}

			CMap m = new CMap();
			m.put("user", u.put());
			m.put("protocol", "WSChat2");
			m.put("address", "ws://127.0.0.1:1999/im/");
			m.put("token", "114514");//createToken(u, -1, 86400000));
			m.put("ok", true);
			return Response.json(ConfigMaster.JSON.toString(m, new CharList()));
		}
	}

	@Route(prefix = true)
	@Accepts(Accepts.GET)
	public Response user__head(Request req, ResponseHeader rh) {
		File img = new File(attDir, pathFilter(req.subDirectory(2).path()));
		System.out.println(img.getAbsolutePath());
		if (!img.isFile()) img = new File(attDir, "default");

		rh.header("Access-Control-Allow-Origin", "*");
		DiskFileInfo info = new DiskFileInfo(img);
		return Response.file(req, info);
	}

	@Route
	@Interceptor("logon")
	@Accepts(Accepts.POST)
	public Object user__set_info(Request req) throws IllegalRequestException {
		User u = (User) req.localCtx().get("USER");

		Map<String, String> x = req.PostFields();

		/*ByteList face = (ByteList) x.get("face");
		if (face != null) {
			try {
				BufferedImage image = ImageIO.read(face.asInputStream());
				if (image != null) ImageIO.write(image, "PNG", new File(headDir, Integer.toString(uid)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}*/
		if (!x.containsKey("name")) return jsonErr("缺数据!");
		u.name = x.get("name");
		u.onDataChanged(this);

		return Response.json("{\"ok\":1}");
	}

	private Response block(Request req, int uid, boolean add, boolean inGroup) {
		return null;
	}

	// region Misc

	private static Response jsonErr(String s) {return Response.json("{\"ok\":0,\"err\":\""+Tokenizer.addSlashes(s)+"\"}");}

	private static MyHashMap<String, Object> initLocal() {
		var L = HttpCache.getInstance().ctx;
		if (!L.containsKey("LST")) {
			L.put("HASH", ILCrypto.HMAC(ILCrypto.SM3()));
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
			sendMessage(g, new Message(0, """
					欢迎使用MyChat2 (下称"软件")
					本软件由Roj234独立开发并依法享有其知识产权

					软件以MIT协议开源,并"按原样提供", 不包含任何显式或隐式的担保,
					上述担保包括但不限于可销售性, 对于特定情况的适应性和安全性
					无论何时，无论是否与软件有直接关联, 无论是否在合同或判决等书面文件中写明
					作者与版权拥有者都不为软件造成的直接或间接损失负责

					[c:red]如不同意本协议, 请勿使用本软件的任何服务[/c]"""),
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
				/*if (len == 0) {
					sendHistory(id, dao.getHistoryCount(id), Collections.emptyList());
					return;
				}

				MutableInt mi = new MutableInt();
				List<Message> msg = dao.getHistory(id, filter, off, len, mi);
				sendHistory(id, mi.getValue(), msg);*/
			}
		}

		@Override
		protected void requestClearHistory(int id, int timeout) {
			AbstractUser u = userMap.get(id);
			if (!(u instanceof User)) return;

			/*ChatDAO.Result r = dao.delHistory(owner.id, id);
			if (r.error != null) {
				sendAlert("无法清除历史纪录: " + r.error);
			}*/
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
}