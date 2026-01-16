package roj.webui;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.RojLib;
import roj.archive.zip.ZipFile;
import roj.collect.HashMap;
import roj.http.server.HttpServer;
import roj.http.server.Router;
import roj.http.server.ZipRouter;
import roj.http.server.auto.OKRouter;
import roj.io.IOUtil;
import roj.net.Net;
import roj.net.ServerLaunch;
import roj.reflect.Reflection;
import roj.reflect.Unsafe;
import roj.text.FastCharset;
import roj.util.DynByteBuf;
import roj.util.NativeException;
import roj.util.OS;
import roj.util.function.ExceptionalConsumer;
import roj.util.function.ExceptionalRunnable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;

import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2026/01/17 04:22
 */
public final class WebUI implements Closeable {
	static {RojLib.fastJni();}

	private static final Map<String, WebUI> instances = new HashMap<>();

	/**
	 *
	 * @param cacheDir 缓存目录，同时也是实例ID
	 */
	public static WebUI create(String cacheDir) throws IOException {return create(cacheDir, null, null);}
	public static WebUI create(String cacheDir, String browserPath) throws IOException {return create(cacheDir, browserPath, null);}
	public static WebUI create(String cacheDir, String browserPath, String extraArguments) throws IOException {
		synchronized (instances) {
			var instance = instances.get(cacheDir);
			if (instance == null) {
				instance = new WebUI(cacheDir, browserPath, extraArguments);
				instances.put(cacheDir, instance);
			}
			return instance;
		}
	}

	private final String instanceId, browserPath, extraArguments;
	private final CacheManager cache;
	private final DynByteBuf structs;
	private final ServerLaunch server;
	private final OKRouter router;
	private final Map<File, ZipFile> jars = new HashMap<>();

	private ProcessHandle processHandle;
	private long pid;
	private long windowHandle;
	private String defaultPath;

	private WebUI(String instanceId, String browserPath, String extraArguments) throws IOException {
		this.instanceId = instanceId;
		this.browserPath = browserPath;
		this.extraArguments = extraArguments;
		this.router = new OKRouter();
		this.structs = DynByteBuf.allocateDirect(1024, 1024);

		try {
			server = HttpServer.simple(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 64, router);
			cache = new CacheManager(instanceId);
			cache.acquire();
		} catch (Exception e) {
			close();
			throw e;
		}
	}

	@CheckReturnValue
	public RPCBuilder newApplication(String appid) {return new RPCBuilder(appid, Reflection.getCallerClass(2));}

	public final class RPCBuilder {
		private final String prefix;
		private final Class<?> caller;

		private final Map<Class<?>, ExceptionalConsumer<?, IOException>> callbacks = new HashMap<>();
		private ExceptionalRunnable<IOException> windowClosed, windowOpened;
		private boolean parallel;

		public RPCBuilder(String prefix, Class<?> caller) {
			this.prefix = prefix.isEmpty() ? "" : prefix.concat("/");
			this.caller = caller;
		}

		public RPCBuilder resource(String relativePath) throws IOException {
			File jar = IOUtil.getJar(caller);
			ZipFile zip = jars.get(jar);
			if (zip == null) jars.put(jar, zip = new ZipFile(jar));
			return resource(new ZipRouter(zip, relativePath));
		}
		public RPCBuilder resource(Router index) {
			router.addPrefixDelegation(prefix, index);
			return this;
		}

		@Deprecated
		public RPCBuilder sends(Class<?> type) {
			callbacks.put(type, null);
			return this;
		}
		@Deprecated
		public <T> RPCBuilder on(Class<T> type, ExceptionalConsumer<T, IOException> callback) {
			callbacks.put(type, callback);
			return this;
		}

		public RPCBuilder windowClosed(ExceptionalRunnable<IOException> close) {
			this.windowClosed = close;
			return this;
		}
		public RPCBuilder windowOpened(ExceptionalRunnable<IOException> open) {
			this.windowOpened = open;
			return this;
		}
		public RPCBuilder parallel(boolean parallel) {
			this.parallel = parallel;
			return this;
		}

		@CheckReturnValue
		public RPCInstance build() {
			RPCInstance rpc = new RPCInstance("WebUI/"+IOUtil.getBaseName(instanceId)+"/"+prefix, caller, callbacks, windowClosed, windowOpened, parallel);
			router.register(rpc, prefix);
			return rpc;
		}
	}

	public synchronized void launch(String appid, int windowWidth, int windowHeight) throws IOException {
		String serverUrl = getServerUrl()+appid+(appid.isEmpty()?"":"/");

		if (processHandle == null || !processHandle.isAlive()) {
			server.launch();
			pid = launch0(serverUrl, windowWidth, windowHeight);
			processHandle = ProcessHandle.of(pid).get();
		} else {
			launch0(serverUrl, windowWidth, windowHeight);
		}
	}
	private int launch0(String serverUrl, int windowWidth, int windowHeight) {
		structs.clear();
		structs.wIndex(32);

		long browser_path = encodeString(browserPath, structs, FastCharset.UTF8());
		long extra_arguments = encodeString(extraArguments, structs, FastCharset.UTF8());

		long ptr = structs.address();

		U.putAddress(ptr, browser_path);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putAddress(ptr, extra_arguments);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putShort(ptr, (short) windowWidth);
		ptr += 2;
		U.putShort(ptr, (short) windowHeight);
		ptr += 2;
		U.putShort(ptr, (short) 0/*x*/);
		ptr += 2;
		U.putShort(ptr, (short) 0/*y*/);
		ptr += 2;

		int error = launch0(serverUrl, instanceId, structs.address());
		if (error != 0) throw new RuntimeException("Error: "+error);

		return U.getInt(ptr);
	}
	private static native int launch0(@NotNull String url, @NotNull String cacheDir, long pOptions) throws NativeException;

	public String getServerUrl() throws IOException {return "http://"+Net.toString(server.localAddress())+"/";}

	/**
	 * 在通过某种方式确定浏览器窗口已经打开后调用
	 */
	private void onConnected() {this.windowHandle = bindWindow0(pid);}
	private static native long bindWindow0(long pOptions) throws NativeException;

	/**
	 * 关闭浏览器并销毁资源
	 */
	public synchronized void close() {
		System.out.println("Waiting for browser to close...");
		boolean terminated = processHandle.destroy();
		cache.release();
		structs.release();
	}

	/**
	 * 设置文件选择器的默认路径
	 */
	public WebUI setDefaultPath(String path) {this.defaultPath = path;return this;}

	private static final int PICKER_OPEN_FILE = 1, PICKER_SAVE_FILE = 2, PICKER_SELECT_FOLDER = 3;

	@Nullable public String pickFile(String title, String filter_name, String filter_pattern) {return pickFile(PICKER_OPEN_FILE, title, defaultPath, null, filter_name, filter_pattern);}
	@Nullable public String pickFolder(String title) {return pickFile(PICKER_SELECT_FOLDER, title, defaultPath, null, null, null);}
	@Nullable public String saveFile(String title, String default_name, String filter_name, String filter_pattern) {return pickFile(PICKER_SAVE_FILE, title, defaultPath, default_name, filter_name, filter_pattern);}

	private synchronized String pickFile(int mode, String title, String default_path, String default_name, String filter_name, String filter_pattern) {
		structs.clear();
		structs.wIndex(64);

		var nativeCharset = OS.CURRENT == OS.WINDOWS ? FastCharset.UTF16() : FastCharset.UTF8();

		// well actually this is FFI, but 我想少写点C多写点Lava，这些东西以后都是我加新语法的灵感（
		long title_address = encodeString(title, structs, nativeCharset);
		long default_path_address = encodeString(default_path, structs, nativeCharset);
		long default_name_address = encodeString(default_name, structs, nativeCharset);
		long filter_name_address = encodeString(filter_name, structs, nativeCharset);
		long filter_pattern_address = encodeString(filter_pattern, structs, nativeCharset);

		long ptr = structs.address();

		U.putAddress(ptr, title_address);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putAddress(ptr, default_path_address);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putAddress(ptr, default_name_address);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putAddress(ptr, filter_name_address);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putAddress(ptr, filter_pattern_address);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putAddress(ptr, windowHandle);
		ptr += Unsafe.ADDRESS_SIZE;
		U.putByte(ptr, (byte) mode);

		return pickFile0(structs.address());
	}
	private static native String pickFile0(long pOptions) throws NativeException;

	private static long encodeString(String s, DynByteBuf memory, FastCharset nativeCharset) {
		if (s == null) return 0;
		long address = memory.address() + memory.wIndex();
		nativeCharset.encodeFully(s, memory);
		if (nativeCharset == FastCharset.UTF8()) memory.put(0);
		else memory.putShort(0);
		return address;
	}
}
