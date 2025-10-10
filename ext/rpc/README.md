好的，这是一份为你的RPC模块编写的 `README.md` 文件。

# Roj RPC Module

## 介绍
`roj-rpc` 是一个轻量级且高效的Java远程过程调用（RPC）框架，旨在简化分布式系统中服务间通信的复杂度。它允许您通过接口定义远程服务，并透明地在客户端调用这些服务，如同调用本地对象一样。该模块利用字节码生成技术（ASM）动态创建客户端代理和服务端调用器，并通过 `MessagePack` 进行高效的数据序列化。

## 特性
*   **基于接口的RPC**: 仅需定义Java接口即可构建RPC服务。
*   **动态代理生成**: 客户端和服务器端代理及调用器均通过ASM动态生成，无需手动实现桩代码。
*   **底层通道无关**: 基于 `roj.net.MyChannel` 进行通信，易于集成到任何支持 `MyChannel` 的网络层。
*   **高效序列化**: 使用 `MessagePack` (`roj.config.ObjectMapper` 配合 `MsgPackEncoder`/`MsgPackParser`) 进行数据传输，确保紧凑和高性能。
*   **异步执行**: 服务端方法调用在独立的 `Executor` 线程池中执行，避免阻塞网络I/O线程。
*   **透明的异常处理**: 远程方法抛出的异常会被捕获并封装为 `RemoteException` 传递回客户端。
*   **简洁易用的API**: 提供直观的API来注册服务和获取客户端代理。

## 快速入门

### 1. 定义RPC接口
所有RPC服务接口必须继承 `roj.net.rpc.api.RemoteProcedure` 接口。这个接口是RPC框架的标记接口，用于识别哪些接口可以进行远程调用。

```java
// example
// roj.net.rpc.api.FirewallManager (假设这个文件在api包下作为示例)
public interface FirewallManager extends RemoteProcedure {
    boolean addFirewallRule(String source, int port, int timeoutSeconds);
    boolean removeFirewallRule(String source, int port);
}
```

### 2. 实现RPC接口 (服务端)
在服务端，您需要提供RPC接口的具体实现类。

```java
public class FirewallManagerImpl implements FirewallManager {
    @Override
    public boolean addFirewallRule(String source, int port, int timeoutSeconds) {
        System.out.println("[Server] received addFirewallRule: source=" + source + ", port=" + port + ", timeout=" + timeoutSeconds + "s");
        // 这里可以进行实际的业务逻辑处理
        // 例如：将规则写入防火墙配置
        return true;
    }

    @Override
    public boolean removeFirewallRule(String source, int port) {
        System.out.println("[Server] received removeFirewallRule: source=" + source + ", port=" + port);
        // 这里可以进行实际的业务逻辑处理
        // 例如：从防火墙配置中移除规则
        return true;
    }
}
```

### 3. 设置RPC服务器
初始化 `RPCServerImpl`，注册您的服务实现，并将其附加到网络通道 `MyChannel` 上。`RPCServerImpl` 需要一个 `Executor` 来异步处理远程方法调用。

```java
// roj.net.MyChannel 是一个抽象网络通道接口，可以是对本地Socket、嵌入式通道等的封装
// TaskPool.cpu() 是一个示例的 Executor 提供者
RPCServerImpl server = new RPCServerImpl(TaskPool.cpu());
server.registerImplementation(FirewallManager.class, new FirewallManagerImpl()); // 注册接口及其实现

ServerLaunch.tcp().initializer(ch -> {
	server.attachTo(ch); // 将服务器逻辑附加到通道
}).launch();

System.out.println("RPC Server is listening...");
```

### 4. 设置RPC客户端
初始化 `RPCClientImpl`，并将其附加到链接RPC服务器的网络通道 `MyChannel` 上。

```java
RPCClientImpl client = new RPCClientImpl();
MyChannel clientChannel = ClientLaunch.tcp().initializer(ch -> {
	client.attachTo(ch); // 将客户端逻辑附加到通道
}).launch();

System.out.println("RPC Client connected to server.");
```

### 5. 调用远程方法
通过 `RPCClient.getImplementation()` 获取远程接口的代理实例。然后，您就可以像调用本地对象一样调用其方法。

```java
FirewallManager firewall = client.getImplementation(FirewallManager.class);

// 调用远程方法
System.out.println("Client invoking addFirewallRule...");
boolean addSuccess = firewall.addFirewallRule("10.0.0.1", 12345, 120);
System.out.println("Client received addFirewallRule result: " + addSuccess);

System.out.println("Client invoking removeFirewallRule...");
boolean removeSuccess = firewall.removeFirewallRule("10.0.0.1", 12345);
System.out.println("Client received removeFirewallRule result: " + removeSuccess);
```

### 完整示例 (使用 `EmbeddedChannel`)
下面的示例展示了如何在一个进程内，使用 `roj.net.channel.EmbeddedChannel.createPair()` 模拟客户端和服务器之间的通信。

```java
import roj.net.channel.EmbeddedChannel; // 假设包名为 roj.net.channel
import roj.net.MyChannel;
import roj.net.rpc.RPCClientImpl;
import roj.net.rpc.RPCServerImpl;
import roj.net.rpc.api.RemoteProcedure;
import roj.concurrent.TaskPool; // 假设 TaskPool 位于 roj.concurrent

// 1. 定义RPC接口
public interface FirewallManager extends RemoteProcedure {
    boolean addFirewallRule(String source, int port, int timeoutSeconds);
    boolean removeFirewallRule(String source, int port);

    // 2. 模拟主程序/入口点
    public static void main(String[] args) throws Exception {
        // 创建一对嵌入式通道，模拟客户端和服务器之间的连接
        MyChannel[] pair = EmbeddedChannel.createPair();
        MyChannel serverEnd = pair[0];
        MyChannel clientEnd = pair[1];

        // --- 服务端设置 ---
        var server = new RPCServerImpl(TaskPool.cpu()); // 服务端需要一个Executor处理请求
        server.attachTo(serverEnd); // 绑定到服务器端通道
        // 3. 注册服务实现
        server.registerImplementation(FirewallManager.class, new FirewallManagerImpl());
        System.out.println("RPC Server initialized and service registered.");

        // --- 客户端设置 ---
        var client = new RPCClientImpl();
        client.attachTo(clientEnd); // 绑定到客户端通道
        System.out.println("RPC Client initialized.");

        // --- 客户端调用 ---
        // 4. 获取远程接口的代理实现
        FirewallManager implementation = client.getImplementation(FirewallManager.class);
        
        // 5. 调用远程方法
        System.out.println("\n--- Client initiates RPC call: addFirewallRule ---");
        boolean resultAdd = implementation.addFirewallRule("10.0.0.1", 12345, 120);
        System.out.println("--- Client received result for addFirewallRule: " + resultAdd + " ---\n");

        System.out.println("--- Client initiates RPC call: removeFirewallRule ---");
        boolean resultRemove = implementation.removeFirewallRule("10.0.0.1", 12345);
        System.out.println("--- Client received result for removeFirewallRule: " + resultRemove + " ---\n");

        // 清理资源 (对于EmbeddedChannel可能不是严格必需，但良好实践)
        serverEnd.close();
        clientEnd.close();
        System.out.println("Channels closed.");
    }
}

// 供示例使用的 FirewallManagerImpl
class FirewallManagerImpl implements FirewallManager {
    @Override
    public boolean addFirewallRule(String source, int port, int timeoutSeconds) {
        System.out.println("[Server Logic] Adding rule: source=" + source + ", port=" + port + ", timeout=" + timeoutSeconds + "s");
        return true;
    }

    @Override
    public boolean removeFirewallRule(String source, int port) {
        System.out.println("[Server Logic] Removing rule: source=" + source + ", port=" + port);
        return true;
    }
}
```

## 核心组件

*   `RemoteProcedure` (`roj.net.rpc.api`): 一个标记接口，所有可远程调用的服务接口都必须继承它。
*   `RPCClient` (`roj.net.rpc.api`): 客户端接口，定义了获取远程服务代理的方法 `getImplementation()`.
*   `RPCServer` (`roj.net.rpc.api`): 服务端接口，定义了注册服务实现的方法 `registerImplementation()`.
*   `RPCClientImpl` (`roj.net.rpc`): `RPCClient` 的具体实现，负责：
    *   通过ASM生成RPC接口的客户端代理。
    *   管理远程方法的调用请求(`PInvokeMethod`)和响应(`PInvocationResult`/`PInvocationFailure`)。
    *   处理远程方法ID的查询(`PQueryMethods`/`PRemoteMethods`)和缓存。
    *   将远程异常 `Throwable` 包装成 `RemoteException`。
*   `RPCServerImpl` (`roj.net.rpc`): `RPCServer` 的具体实现，负责：
    *   管理已注册的RPC服务实现。
    *   通过ASM动态生成服务端的 `Invoker` (调用器) 来高效地分发和执行客户端请求。
    *   将远程方法调用任务提交给内部的 `Executor` (例如 `TaskPool.cpu()`) 异步处理。
    *   将方法执行结果或异常返回给客户端。
*   `RemoteException` (`roj.net.rpc`): RPC模块使用的自定义运行时异常，用于封装和传递在远程服务器上发生的异常到客户端。

## 序列化
本RPC模块使用 `roj.config.ObjectMapper` 结合 `roj.config.MsgPackEncoder` 和 `roj.config.MsgPackParser` 进行方法参数和返回值的序列化与反序列化。`MessagePack` 是一种高效的二进制序列化格式，确保了数据传输的紧凑性和速度。

## 异常处理
当RPC服务器在执行远程方法时捕获到异常，它会将其封装在数据包中发送回客户端。客户端接收到此数据包后，会将其反序列化并包装成 `roj.net.rpc.RemoteException` 重新抛出，从而使远程异常对客户端应用程序来说是透明的。
