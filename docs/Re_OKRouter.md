# OK HTTP Server - Lightweight Java HTTP Server


OK HTTP Server 是一个高性能、轻量级的 Java HTTP 服务器框架，支持 HTTP/2。
核心设计强调简洁性和可扩展性，通过函数式接口（如 `Router` 和 `Content`）处理请求/响应。
内置零拷贝支持、压缩、Session 管理和 CORS 等功能，适合嵌入式应用、微服务或自定义 Web 服务器。
OKRouter 是其注解驱动路由扩展，使用 ASM 动态生成代码，支持路径参数和拦截器。

## 特性

### 核心特性
- **高性能 NIO 基础**：线程本地资源池（请求、Deflater、Keep-Alive），支持零拷贝 `sendfile`（文件传输）和异步响应。
- **HTTP/1.1 & HTTP/2**：自动升级到 HTTP/2，支持多路复用（通过 `H2Upgrade`）。
- **流式响应**：`Content` 接口统一处理文本、JSON、文件、WebSocket 和错误页；支持 GZIP/Deflate 压缩。
- **请求处理**：自动解析 Query/Form/Cookie/Body，支持自定义 `BodyParser`（Multipart/UrlEncoded/JSON）。
- **实用功能**：
  - Session 管理（Cookie-based，可注入 `SessionStorage`）。
  - CORS 支持（预检和简单请求）。
  - 认证（Basic/Bearer）和代理验证（`x-proxy-*` 头）。
  - 错误处理：用户友好页面，钩子 `onUncaughtError`。
- **配置灵活**：可自定义超时、头部大小、POST 限制；Builder 模式启动服务器。
- **资源管理**：池化缓冲、显式释放，减少 GC 压力。

### OKRouter 特性（注解路由扩展）
- **注解驱动**：`@Route`、`@GET`、`@POST` 等定义路由，支持路径参数、正则和通配符。
- **参数注入**：自动从查询/表单/头部/Body 注入，支持 JSON/表单反序列化。
- **拦截器**：预/后处理器链，可全局/局部注册。
- **MIME 支持**：自动设置响应 Content-Type。
- **代理路由**：前缀委托到其他路由器。

## 快速开始

### 1. 核心服务器启动（无路由）
使用 `HttpServer.simple()` 创建简单服务器。实现 `Router` 接口处理请求。

```java
import roj.http.server.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.locks.LockSupport;

public class SimpleServer {
  public static void main(String[] args) throws IOException {
    // 创建服务器：地址、backlog、Router
    Router router = new Router() {
      @Override
      public Content response(Request req, Response resp) {
        if ("GET".equals(req.actionName())) {  // req.action() 为 int
          return Content.html("<h1>Hello, " + req.path() + "!</h1>");
        } else {
          return Content.httpError(405);  // Method Not Allowed
        }
      }
    };

    ServerLaunch launch = HttpServer.simple(new InetSocketAddress(8080), 128, router)
            .option(StandardSocketOptions.SO_REUSEADDR, true);
    launch.start();
    LockSupport.park();
  }
}
```

- 访问 `http://localhost:8080/test` 将返回 Hello 页面。
- 配置：添加日志 `HttpServer.setLevel(Level.DEBUG)`；自定义错误 `HttpServer.globalHttpError = code -> ...`。

### 2. 使用 OKRouter（注解路由）
OKRouter 简化路由定义，通过注解和 ASM 自动生成 `Router` 实现。

#### 定义控制器
```java
import roj.http.server.*;
import roj.http.server.auto.*;  // OKRouter 注解

@Interceptor("auth") // 为类中所有方法添加拦截器
public class UserController {
    // GET /users/:id（路径参数）
    @Route("/users/:id")
    @Accepts(Accepts.GET)
    public String getUser(Request req, @RequestParam("id") int id) {
        return "User ID: " + id;
    }

    // POST /users，JSON Body 注入
    @POST("/users")
    public String createUser(@Body User user) {
        return "Created: " + user.name;
    }

    // 拦截器：检查登录
    @Interceptor("auth")
    public Content checkAuth(Request req) {
        if (!hasValidToken(req)) {
            req.response().code(401);
            return Content.text("Unauthorized");
        }
        return null;  // 继续
    }
}

// User 类示例
class User {
    public String name;
}
```

#### 注册并启动
```java
import roj.http.server.*;
import roj.http.server.OKRouter;

public class RouterServer {
    public static void main(String[] args) throws IOException {
        OKRouter okRouter = new OKRouter(true);
        okRouter.register(new UserController());

        // 添加前缀
        okRouter.register(new AdminController(), "/admin");

        // 全局拦截器
        okRouter.setGlobalInterceptors("auth");

        // 代理其他路由器
        okRouter.addPrefixDelegation("/api/**", anotherRouter);

        // 启动服务器
        HttpServer.simple(new InetSocketAddress(8080), 128, okRouter).startAndWait();
    }
}
```

- 测试：`GET /users/123` 返回 "User ID: 123"；`POST /users` with JSON `{"name":"Alice"}` 返回 "Created: Alice"。

## 高级用法

### 自定义 SessionStorage
```java
HttpServer.globalSessionStorage = FileSessionStorage({
    baseDir: File("session"),
    concurrency: 4,
    LRUSizePerConcurrency: 1024
});
```

### WebSocket 示例
```java
// 在 Router.response()
if (req.header("upgrade").equalsIgnoreCase("websocket")) {
    return Content.websocket(req, r -> new MyWebSocketHandler(r));
}
```

### 全局回调（OKRouter）
```java
okRouter.onFinish((req, success) -> { /* 清理日志 */ return true; });
```

### 文件上传
在checkHeader中设置BodyParser为MultipartParser或类似即可
默认小文件内存缓存，大文件临时目录；若有足够数据，可继承并自定义保存目录(覆盖begin函数，并调用_setExternalFile)，无需移动临时文件

## 注意事项
- **性能**：`Router#content()` 同步减少TTFB，耗时长考虑 `async()` 避免阻塞。
- **兼容**：HTTP/2 可ALPN或PRI升级，允许HTTP/2 without TLS。
- **限制**：默认 POST 4KB；头部 5KB。路由器实现按需覆盖。
- **安全**：自定义 `BodyParser` 时验证输入（防注入）。Session ID 验证 `isValid()` 防篡改。
- **ASM 依赖**：OKRouter 需要类可见（ClassLoader）；调试模式下打印字节码错误。
- **错误处理**：未捕获异常调用 `HttpServer.onUncaughtError` 生成页面， `Content.internalError()` 由业务代码使用。
