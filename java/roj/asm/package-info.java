/**
 * 项目名称：Vyper<p>
 * Viper + Hyper<p>
 * 快，bug和feature也可能把你毒死(雾 <p>
 * 一个高性能的Java字节码操作框架，支持按需解析和常量池模式。
 *
 * <h2>核心理念</h2>
 * 本项目实现了比ObjectWeb ASM多快好省的字节码处理，包括但不限于以下独特设计：
 * <ul>
 *   <li><b>按需解析</b> - 完成Class/Method/Field节点的基础解析后，所有{@link roj.asm.attr.Attribute 属性}的复杂结构仅在需要时解析</li>
 *   <li><b>代码访问者</b> - 整体采用Tree模式结构化存储，同时为{@link roj.asm.insn.AttrCode Code}属性提供{@link roj.asm.insn.CodeVisitor 访问者模式接口}</li>
 *   <li><b>指令压缩</b> - {@link roj.asm.insn.InsnList}采用压缩的内存布局，{@link roj.asm.insn.InsnNode}仅为动态视图</li>
 *   <li><b>常量模式</b> - 通过{@link roj.asm.ClassNode#cp}直接操作常量池，使用{@link roj.asmx.TransformUtil#compress(roj.asm.ClassNode)}优化代码大小</li>
 * </ul>
 *
 * <h2>典型用例</h2>
 * <pre>{@code
 * // 按需解析模式
 * ClassNode clazz = ClassNode.parseSkeleton(bytes);
 *
 * // 混合模式操作
 * clazz.methods.forEach(method -> {
 *     AttrCode code = method.getAttribute(clazz.cp, Attribute.Code);
 *     if (code != null) {
 *         code.instructions.insert(...);            // Tree模式
 *     }
 *
 *     CodeVisitor visitor = new CodeVisitor() { ... }
 *     UnparsedAttribute attribute = method.getRawAttribute("Code");
 *     if (code != null) {
 *         visitor.visit(clazz.cp, attribute.getRawData());    // Visitor模式
 *     }
 * });
 *
 * // 常量池模式
 * CstUTF utf = clazz.cp.getUtf("aaa");
 * clazz.cp.setUTFValue(utf, "bbb");
 * }</pre>
 *
 * <h2>性能指标</h2>
 * <table>
 *   <tr><th>特性</th><th>收益</th></tr>
 *   <tr>
 *     <td>指令压缩</td>
 *     <td>内存占用 ≈ byte[]</td>
 *   </tr>
 *   <tr>
 *     <td>按需解析</td>
 *     <td>解析时间平均降低70%</td>
 *   </tr>
 *   <tr>
 *     <td>常量模式</td>
 *     <td>修改常量池中的对象引用，甚至无需解析Code属性 —— 请看 {@link roj.asmx.mapper.Mapper} 在基准测试中能比竞品快90%+</td>
 *   </tr>
 * </table>
 *
 * <h2>扩展点</h2>
 * 通过实现以下接口定制行为：
 * <ul>
 *   <li>{@link roj.asm.attr.Attribute#addCustomAttribute(roj.util.TypedKey, java.util.function.BiFunction)} - 添加对新JVM属性的支持</li>
 * </ul>
 *
 * @author Roj234-N
 * @version 4.0
 * @since 2021/1/3 15:59
 * @see roj.asm.ClassNode
 * @see roj.asm.insn.InsnList
 * @see roj.asmx.injector.CodeWeaver
 * @see roj.asmx.Transformer
 */
@CompileOnly
package roj.asm;

import roj.plugins.ci.annotation.CompileOnly;