package roj.plugins;

import roj.archive.zip.ZipArchive;
import roj.collect.CollectionX;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.crypt.KeyType;
import roj.crypt.jar.JarVerifier;
import roj.io.IOUtil;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.ui.Terminal;
import roj.ui.terminal.Argument;
import roj.ui.terminal.Command;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static roj.ui.terminal.CommandNode.argument;
import static roj.ui.terminal.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/7/16 0016 5:08
 */
@SimplePlugin(id = "keyStore", desc = "统一密钥管理器，同时提供jar签名和验证功能")
public class KeyStorePlugin extends Plugin {
	private final Map<String, List<X509Certificate>> certificates = new MyHashMap<>();
	private final Map<String, PrivateKey> privateKeys = new MyHashMap<>();

	public Argument<KeyPair> asKeyPair() {return Argument.oneOf(CollectionX.toMap(privateKeys.keySet(), name -> new KeyPair(certificates.get(name).get(0).getPublicKey(), privateKeys.get(name))));}

	@Override
	protected void onEnable() throws Exception {
		Command load = ctx -> {
			var pem = ctx.argument("证书pem", File.class);
			var key = ctx.argument("私钥key", File.class);
			var name = ctx.argument("别名", String.class, IOUtil.fileName(key.getName()));

			var cf = CertificateFactory.getInstance("X509");
			var certs = new SimpleList<X509Certificate>();
			try (var in = new BufferedInputStream(new FileInputStream(pem))) {
				while (true) {
					certs.add((X509Certificate) cf.generateCertificate(in));

					in.mark(1);
					if (in.read() < 0) break;
					in.reset();
				}
			}

			var prk = (PrivateKey) KeyType.getInstance(certs.get(0).getPublicKey().getAlgorithm()).fromPEM(IOUtil.readString(key));
			certificates.put(name, certs);
			privateKeys.put(name, prk);
		};

		registerCommand(literal("ksload")
			.then(argument("证书pem", Argument.file())
				.then(argument("私钥key", Argument.file()).executes(load)
					.then(argument("别名", Argument.string()).executes(load)))));
		registerCommand(literal("ksunload")
			.then(argument("别名", Argument.oneOf(CollectionX.toMap(certificates.keySet()))).executes(ctx -> {
				String name = ctx.argument("别名", String.class);
				certificates.remove(name);
				privateKeys.remove(name);
			})));

		List<String> hashAlgs = Arrays.asList("SHA-512", "SHA-384", "SHA-256");

		Command sign = ctx -> {
			var jar = ctx.argument("jar", File.class);
			var alias = ctx.argument("证书别名", String.class);
			var hashAlg = ctx.argument("摘要算法", String.class, "SHA-256");
			var signHashAlg = ctx.argument("签名摘要算法", String.class, "SHA-256");

			getLogger().info("正在签名……");
			try(var zf = new ZipArchive(jar)) {
				String error = JarVerifier.signJar(zf, hashAlg, signHashAlg, certificates.get(alias), privateKeys.get(alias), alias.toUpperCase(Locale.ROOT));
				if (error != null) System.err.println("签名失败: " + error);
				else zf.save();
				Terminal.success("签名完成");
			}
		};

		registerCommand(literal("jar").then(argument("jar", Argument.file()).then(literal("verify").executes(ctx -> {
			JarVerifier.main(new String[]{ctx.argument("jar", File.class).getAbsolutePath()});
		})).then(literal("sign")
			.then(argument("证书别名", Argument.oneOf(CollectionX.toMap(privateKeys.keySet())))
					.then(argument("摘要算法", Argument.suggest(CollectionX.toMap(hashAlgs))).executes(sign)
						.then(argument("签名摘要算法", Argument.suggest(CollectionX.toMap(hashAlgs))).executes(sign)))))));
	}
}