package roj.staging;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.asm.ClassNode;
import roj.asm.type.Signature;
import roj.crypt.jar.JarVerifier;
import roj.io.IOUtil;
import roj.ui.Argument;
import roj.ui.OptionParser;

import java.io.File;
import java.io.InputStream;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;

/**
 * 字节码解析器
 * @author Roj234
 * @version 3.1
 * @since 2021/5/29 17:16
 */
public final class Javap {
	public static void main(String[] args) throws Exception {
		var dump = literal("dump").comment("使用RojASM解析类结构").then(argument("文件", Argument.file()).executes(ctx -> {
			System.out.println(ClassNode.parseAll(IOUtil.read(ctx.argument("文件", File.class))));
		})).then(argument("类名", Argument.string()).executes(ctx -> {
			var name = ctx.argument("类名", String.class);
			InputStream in = Javap.class.getClassLoader().getResourceAsStream(name.replace('.', '/') + ".class");
			if (in == null) System.out.println("找不到"+name+"的类文件");
			else System.out.println(ClassNode.parseAll(IOUtil.read(in)));
		}));
		var dumpSignature = literal("dump-type").comment("使用RojASM解析类型签名").then(argument("签名", Argument.string()).executes(ctx -> {
			var signature = Signature.parse(ctx.argument("签名", String.class));
			System.out.println("getTypeParam(): "+signature.getTypeParam(IOUtil.getSharedCharBuf()).toString());
			System.out.println("toString(): "+signature);
			System.out.println("toDesc(): "+signature.toDesc());
			signature.validate();
		}));
		var verify = literal("verify").comment("验证Jar签名").then(argument("文件", Argument.file()).executes(ctx -> {
			try (var zf = new ZipFile(ctx.argument("文件", File.class))) {
				System.out.println("正在验证"+zf.toString());
				JarVerifier verifier = JarVerifier.create(zf);
				if (verifier == null) {
					System.out.println("文件没有清单属性");
					return;
				}
				if (!verifier.isSigned()) {
					System.out.println("文件没有签名");
					return;
				}

				verifier.ensureManifestValid(false);
				System.out.println("清单和元签名校验通过");
				System.out.println("是自签证书:"+!verifier.isSignTrusted());
				System.out.println("签名算法:"+verifier.getAlgorithm());

				for (ZEntry entry : zf.entries()) {
					try (InputStream in = verifier.wrapInput(entry.getName(), zf.getStream(entry))) {
						IOUtil.read(in);
					} catch (Exception e) {
						System.out.println(e.getMessage());
					}
				}

				System.out.println("Jar验证完成，有问题的文件已在上方列出");
			}
		}));

		new OptionParser("Javap").add(dump).add(verify).add(dumpSignature).parse(args, true);
	}
}