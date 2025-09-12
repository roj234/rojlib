# (并不是很)安全的插件系统
 * 热重载 -> 卸载插件后，类也会卸载
 * 安全 -> DPSSecurityManager类中写了几十个ASM method hook
 * 界面 -> AnsiString和CommandConsole，至少用起来很不错

## 权限
  * accessUnsafe 允许获取Unsafe
  * dynamicLoadClass 允许动态使用ClassLoader
  * loadNative 允许加载native
  * reflectivePackage 允许通过反射访问的包前缀
  * extraPath 允许访问的路径前缀

上述功能不依赖SecurityManager  
虽然并没有办法保证真正的安全(a.k.a 随便造)  
 * method hook可能没写全
 * JVM本来也不以安全著称，绝对安全连docker也不够，群友觉得VM都不够要搞物理隔离了  

但是可以让恶意插件必须在plugin.yml中声明这些要求