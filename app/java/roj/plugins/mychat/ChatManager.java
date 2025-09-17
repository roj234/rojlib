package roj.plugins.mychat;

import org.jetbrains.annotations.Nullable;
import roj.collect.IntMap;
import roj.config.ConfigMaster;
import roj.config.JsonSerializer;
import roj.config.node.MapValue;
import roj.http.server.*;
import roj.http.server.auto.*;
import roj.io.IOUtil;
import roj.plugin.PermissionHolder;
import roj.plugin.Plugin;
import roj.plugin.PluginDescriptor;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.Tokenizer;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * @author solo6975
 * @since 2022/2/7 17:05
 */
@SimplePlugin(id = "mychat")
public class ChatManager extends Plugin {
	static final Set<String> PROTOCOLS = Collections.singleton("WSChat2");

	static File attDir;
	static IntMap<ChatSubject> chatters = new IntMap<>();

	static ChatGroup testGroup;
	Plugin easySso;

	@Override
	protected void onEnable() throws Exception {
		attDir = new File(getDataFolder(), "att");
		if (!attDir.isDirectory() && !attDir.mkdirs()) throw new IOException("Failed to create attachment directory");

		easySso = getPluginManager().getPluginInstance(PluginDescriptor.Role.PermissionManager);

		ChatGroup T = new ChatGroup();
		T.id = 114514;
		T.name = "测试群聊";
		T.desc = "测试JSON数据\n<I>调试模式已经开启!!</I>\nPowered by Async/2.1";
		T.face = "/chat/user/head/1000000";
		chatters.put(T.id, T);
		testGroup = T;

		registerRoute("chat", new OKRouter().register(this).addPrefixDelegation("ui", new ZipRouter(new File(getDataFolder(), "webui.zip"))), "PermissionManager");
		System.out.println("MyChat2 now running");
	}

	@Nullable
	private PermissionHolder getUser(Request req) {return easySso == null ? null : easySso.ipc(new TypedKey<>("getUser"), req);}
	private ChatUser initUsr(Request req) {
		var user = getUser(req);
		if (user == null) return null;

		var myUser = chatters.get(user.getId());
		if (myUser != null) return (ChatUser) myUser;

		var A = new ChatUser();
		A.id = user.getId();
		A.name = user.getName();
		A.desc = "这个人很懒，不写介绍";
		A.face = "/chat/user/head/"+A.id;

		synchronized (chatters) {
			chatters.put(A.id, A);
			testGroup.joinGroup(A);
		}

		return A;
	}

	@Interceptor
	public void logon(Request req, PostSetting ps) throws IllegalRequestException {
		var value = initUsr(req);
		if (value == null) throw new IllegalRequestException(403);
		req.threadLocal().put("USER", value);

		if (ps != null) ps.postAccept(65536, 0);
	}

	@Interceptor
	public Object parallelLimit(Request req, ResponseHeader rh, PostSetting ps) {
		ChatUser u = (ChatUser) req.threadLocal().get("USER");

		if (u.uploadTasks.get() < 0) {
			rh.code(503).header("Retry-After", "60");
			return error("系统繁忙,请稍后再试");
		}

		u.uploadTasks.getAndDecrement();
		rh.onFinish((__, success) -> {
			u.uploadTasks.getAndIncrement();
			return false;
		});

		return null;
	}

	@GET("file/**")
	@Interceptor({"logon","parallelLimit"})
	public Content getFile(Request req, ResponseHeader rh) {
		String safePath = req.path();
		File file = new File(attDir, safePath);
		if (!file.isFile()) return rh.code(404).noContent();
		DiskFileInfo info = new DiskFileInfo(file);
		return Content.file(req, info);
	}

	@Route(value = "file/**")
	@Accepts(Accepts.DELETE)
	@Interceptor("logon")
	public Object deleteFile(Request req) {
		ChatUser u = (ChatUser) req.threadLocal().get("USER");

		String safePath = req.path();
		DynByteBuf bb = IOUtil.decodeBase64(safePath);
		if (bb.readInt() != u.id) {
			return error("没有权限");
		} else {
			File file = new File("attachment/" + safePath);
			if (file.isFile() && file.delete()) return "{\"ok\":1}";
			else return error("删除失败");
		}
	}

	@Interceptor
	public void fileUpload(Request req, ResponseHeader rh, PostSetting ps) {
		ChatUser u = (ChatUser) req.threadLocal().get("USER");

		var paths = TextUtil.split(req.path(), '/');
		boolean img = paths.get(1).contains("img");
		int count = TextUtil.parseInt(paths.get(2));

		ps.postAccept(4194304, 10000);
		ps.postHandler(new UploadHandler(req, count, u.id, img));
	}

	@POST(value = "file/**")
	@Interceptor({"logon","parallelLimit","fileUpload"})
	public String postFile(Request req) {
		UploadHandler ph = (UploadHandler) req.postHandler();

		JsonSerializer ser = new JsonSerializer();
		ser.emitList();

		File[] files = ph.files;
		String[] errors = ph.errors;
		for (int i = 0; i < files.length; i++) {
			ser.emitMap();

			File f = files[i];
			boolean ok = errors == null || errors[i] == null;
			if (ok) files[i] = null;

			ser.emitKey("ok");
			ser.emit(ok);

			ser.emitKey("v");
			ser.emit(ok ? f.getName() : errors[i]);

			ser.pop();
		}
		return ser.getValue().toStringAndFree();
	}

	@GET
	public Content user__info(Request req) {
		ChatUser u = initUsr(req);

		if (req.header("upgrade").equals("websocket")) {
			return Content.websocket(req, req1 -> {
				var user = initUsr(req1);
				var w = new ChatWorker(this, user);
				user.onLogin(this, w, req1);
				return w;
			}, PROTOCOLS);
		}

		synchronized (u) {
			var m = new MapValue();
			m.put("ok", true);
			m.put("user", u.put());
			m.put("address", new File("C:\\Server").isDirectory() ? "wss://popupcandy.dynv6.net:25565/chat/user/info" : "ws://127.0.0.1:8080/chat/user/info");
			m.put("protocol", "WSChat2");
			return Content.json(ConfigMaster.JSON.toString(m, new CharList()));
		}
	}

	@GET("user/head/**")
	public Content user__head(Request req, ResponseHeader rh) {
		File img = new File(attDir, req.path());
		System.out.println(img.getAbsolutePath());
		if (!img.isFile()) img = new File(attDir, "default");

		rh.header("Access-Control-Allow-Origin", "*");
		DiskFileInfo info = new DiskFileInfo(img);
		return Content.file(req, info);
	}

	@POST
	@Interceptor("logon")
	public Object user__set_info(Request req) throws IllegalRequestException {
		ChatUser u = initUsr(req);

		Map<String, Object> x = Helpers.cast(req.formData());

		// TODO HPostHandler
		/*var face = (ByteList) x.get("face");
		if (face != null) {
			try {
				BufferedImage image = ImageIO.read(face.asInputStream());
				if (image != null) ImageIO.write(image, "PNG", new File(attDir, Integer.toString(u.id)));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}*/
		u.name = (String) x.getOrDefault("name", u.name);
		u.desc = (String) x.getOrDefault("desc", u.desc);
		//{desc=这个人很懒，不写介绍, friend=0, resort=false, name=roj234, search=false}
		u.onDataChanged(this);

		return Content.json("{\"ok\":1}");
	}

	private static Content error(String s) {return Content.json("{\"ok\":0,\"err\":\""+Tokenizer.escape(s)+"\"}");}


	public ChatSubject getSubject(int id) {
		return chatters.get(id);
	}
}