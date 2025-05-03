# 注解定义的HTTP路由

 * @Route定义路由，可选value指定路径，或者通过【方法名称】.replace("__", "/")得出
 * @Interceptor定义或使用预处理拦截器，拦截时body还未接受
 * @Body(From.X)决定方法的【自定义参数】来源, GET POST_KV JSON可选，JSON模式只允许一个扩展参数，也就是反序列化的对象
 * 拦截器输入固定为Request, @Nullable PostSetting
 * 路由参数全部可选但必须按顺序：Request ResponseHeader 【其它自定义参数】
 * 返回值只能是void或任意对象类型
 * 如果不是Response也不是null会调用toString
 * [ ] 序列化未来加入
 * 现已支持Vue语法，详见@Route注解或Vue-router主页
 * 通过Request#getArguments()获取路由参数 （Unstable，之后大概会改）

```java
import roj.http.server.*;
import roj.http.server.auto.*;

public class Example {
	public static void main(String[] args) {
		OKRouter okRouter = new OKRouter();
		HttpServer11.simple(new InetSocketAddress(12345), 64, okRouter.register(new Example())).launch();

		// http server is daemon thread
		LockSupport.park();
	}

	public static class AutoDeserializeTest {
		int num;
		String text;
		@Optional
		int wait;
		@Optional
		String comment;
	}

	@Route
	@Interceptor("cors")
	public String push_detail(@Body AutoDeserializeTest body) throws Exception {
		System.out.println(body.num);
		Thread.sleep(body.wait);

		return "null";
	}

	@Route
	@Interceptor({"cors","threadUpload"})
	public String uploadTest(Request req, @QueryParam String path) throws Exception {
		MultipartFormHandler ph = (MultipartFormHandler) req.postHandler();
		if (ph != null) {
			MultipartFormHandler.FormData postData = ph.file("file");
			postData.moveTo(new File(path));
		}
	}

	@Interceptor
	public Content cors(Request req) {
		return req.checkOrigin(null/* a CORSPolicy instance */);
	}

	@Interceptor
	public void threadUpload(Request req, PostSetting post) {
		if (post == null) return;
		post.postHandler(new MultipartFormHandler(req) {
			@Override
			protected void onValue(ChannelCtx ctx, DynByteBuf buf) throws IOException {
				FormData fd = (FormData) map.get(name);
				if (!fd.append(buf)) {
					fd._setExternalFile(new File("D:\\tmp\\tmp_"+System.nanoTime()));
					fd.append(buf);
				}
			}
		});
		post.postAccept(104857600, 99999);
	}
}
```