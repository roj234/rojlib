package roj.crypt.jar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.net.mss.X509CertFormat;
import roj.util.DynByteBuf;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Roj234
 * @since 2024/3/22 0022 19:44
 */
public class JarVerifier {
	private static final MyHashSet<String> VALID_CERTIFICATE_EXTENSION = new MyHashSet<>("RSA", "DSA", "EC");
	private static final List<String> SECURE_HASH_ALGORITHMS = Arrays.asList("SHA-512", "SHA-384", "SHA-256");
	private static final ThreadLocal<Map<String, MessageDigest>> DIGESTS = new ThreadLocal<>();

	private final URL base;
	private final Manifest manifest, signature;
	private final ManifestBytes mb;
	private final byte[] sb;
	private final SignatureBlock block;
	private String algorithm, prevName;
	private CodeSource source;

	public JarVerifier(URL url, Manifest manifest, ManifestBytes mb, Manifest signature, byte[] sb, SignatureBlock block) {
		this.base = url;
		this.manifest = manifest;
		this.mb = mb;
		this.sb = sb;
		this.signature = signature;
		this.block = block;
	}

	public boolean isSigned() { return block != null; }
	public boolean isSignTrusted() {
		List<X509Certificate> certs = block.getCertificates();
		X509Certificate cert = certs.get(0);
		try {
			X509TrustManager tm = X509CertFormat.getDefault();
			tm.checkServerTrusted(certs.toArray(new X509Certificate[0]), cert.getPublicKey().getAlgorithm());
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public void ensureManifestValid() throws GeneralSecurityException {
		if (block == null) {
			this.source = new CodeSource(base, (CodeSigner[]) null);
			return;
		}

		Map<String, MessageDigest> digests = DIGESTS.get();
		if (digests == null) digests = new MyHashMap<>();

		String algorithm = "", prevName = "";

		int flag = 0;
		Attributes main = signature.getMainAttributes();
		for (int i = 0; i < SECURE_HASH_ALGORITHMS.size(); i++) {
			algorithm = SECURE_HASH_ALGORITHMS.get(i);
			String digestStr = main.getValue(algorithm+"-Digest-Manifest");
			if (digestStr != null && (flag & 1) == 0) {
				flag |= 1;

				MessageDigest digest = digester(digests, algorithm);
				digest.update(mb.data);

				byte[] except = Base64.decode(digestStr, IOUtil.getSharedByteBuf()).toByteArray();
				byte[] computed = digest.digest();

				if (!MessageDigest.isEqual(except, computed)) throw new SecurityException("清单校验失败");
			}
			digestStr = main.getValue(algorithm+"-Digest-Manifest-Main-Attributes");
			if (digestStr != null && (flag & 2) == 0) {
				flag |= 2;

				MessageDigest digest = digester(digests, algorithm);
				byte[] computed = mb.digest(digest, null);
				byte[] except = Base64.decode(digestStr, IOUtil.getSharedByteBuf()).toByteArray();

				if (!MessageDigest.isEqual(except, computed)) throw new SecurityException("清单主属性校验失败");
			}

			if (flag == 3) {
				prevName = algorithm+"-Digest";
				break;
			}
		}

		for (Map.Entry<String, Attributes> entry : signature.getEntries().entrySet()) {
			String value = entry.getValue().getValue(prevName);
			block:
			if (value == null) {
				int i = 0;
				do {
					algorithm = SECURE_HASH_ALGORITHMS.get(i);
					value = entry.getValue().getValue(prevName = algorithm+"-Digest");
					if (value != null) break block;
					i++;
				} while (i < SECURE_HASH_ALGORITHMS.size());

				continue;
			}

			MessageDigest digest = digester(digests, algorithm);

			byte[] computed = mb.digest(digest, entry.getKey());
			byte[] except = Base64.decode(value, IOUtil.getSharedByteBuf()).toByteArray();

			if (!MessageDigest.isEqual(except, computed)) throw new SecurityException("清单"+entry.getKey()+"属性校验失败");
		}

		found: {
		for (X509Certificate cert : block.getCertificates()) {
			if (!cert.getSerialNumber().equals(block.getSignerSn())) continue;

			Signature sign = Signature.getInstance(block.getSignatureAlg());
			sign.initVerify(cert.getPublicKey());
			sign.update(sb);
			if (!sign.verify(block.getSignature())) throw new SecurityException("元签名校验失败");

			break found;
		}
		throw new SecurityException("未找到序列号匹配的证书");
		}

		this.algorithm = algorithm;
		this.prevName = prevName;
		this.source = new CodeSource(base,  new CodeSigner[]{new CodeSigner(block, null)});
		DIGESTS.set(digests);
	}
	public Manifest getManifest() { return manifest; }
	public CodeSource getCodeSource() { return source; }

	private static MessageDigest digester(Map<String, MessageDigest> digests, String algorithm) throws NoSuchAlgorithmException {
		MessageDigest digest = digests.get(algorithm);
		if (digest == null) digests.put(algorithm, digest = MessageDigest.getInstance(algorithm));
		return digest;
	}

	public InputStream wrapInput(String name, InputStream in) {
		Attributes attr = manifest.getAttributes(name);
		if (attr == null) return in;

		String except = attr.getValue(prevName);
		block:
		if (except == null) {
			for (int i = 0; i < SECURE_HASH_ALGORITHMS.size(); i++) {
				algorithm = SECURE_HASH_ALGORITHMS.get(i);
				except = attr.getValue(prevName = algorithm+"-Digest");
				if (except != null) break block;
			}
			return in;
		}

		MessageDigest md;
		try {
			md = digester(DIGESTS.get(), algorithm);
		} catch (NoSuchAlgorithmException e) {
			return in;
		}

		md.reset();
		return new DigestInputStream(in, md, except, name);
	}
	private static final class DigestInputStream extends InputStream {
		private final InputStream in;
		private final MessageDigest md;
		private final String hash;
		private final String name;

		public DigestInputStream(InputStream in, MessageDigest md, String hash, String name) {
			this.in = in;
			this.md = md;
			this.hash = hash;
			this.name = name;
		}

		public int available() throws IOException { return in.available(); }

		public void close() throws IOException { in.close(); }

		public int read() throws IOException {
			int b = in.read();
			if (b >= 0) md.update((byte) b);
			else check();
			return b;
		}

		public int read(@NotNull byte[] b, int off, int len) throws IOException {
			len = in.read(b, off, len);
			if (len >= 0) md.update(b, off, len);
			else check();
			return len;
		}

		private void check() {
			if (!Base64.encode(DynByteBuf.wrap(md.digest()), IOUtil.getSharedCharBuf()).equals(hash)) {
				throw new SecurityException(name+"的"+md.getAlgorithm()+"校验失败");
			}
		}
	}

	@Nullable
	public static JarVerifier create(ZipFile zf) throws IOException {
		InputStream in = zf.getStream("META-INF/MANIFEST.MF");
		if (in == null) return null;

		Manifest manifest = new Manifest(in);
		ManifestBytes mb = null;
		Manifest signature = null;
		byte[] sb = null;
		SignatureBlock signatureBlock = null;

		for (ZEntry entry : zf.entries()) {
			String name = entry.getName();
			String extName = IOUtil.extensionName(name);
			if (name.startsWith("META-INF/") && VALID_CERTIFICATE_EXTENSION.contains(extName)) {
				try {
					signatureBlock = new SignatureBlock(zf.getStream(entry));
				} catch (CertificateException e) {
					throw new IOException(e);
				}

				in = zf.getStream(name.substring(0, name.length() - extName.length())+"SF");
				if (in == null) signatureBlock = null;
				else {
					sb = IOUtil.read(in);
					signature = new Manifest(new ByteArrayInputStream(sb));
					mb = new ManifestBytes(zf.getStream("META-INF/MANIFEST.MF"));
				}

				break;
			}
		}
		URL url = zf.source() instanceof FileSource file ? new URL("file", "", file.getFile().getAbsolutePath().replace(File.separatorChar, '/')) : null;
		return new JarVerifier(url, manifest, mb, signature, sb, signatureBlock);
	}

	public static void main(String[] args) throws Exception {
		try (ZipFile zf = new ZipFile(args[0])) {
			JarVerifier verifier = create(zf);
			if (verifier == null) {
				System.out.println("文件没有清单属性");
				return;
			}
			if (!verifier.isSigned()) {
				System.out.println("文件没有签名");
				return;
			}

			System.out.println("是CA证书:"+verifier.isSignTrusted());
			verifier.ensureManifestValid();

			for (ZEntry entry : zf.entries()) {
				try (InputStream in = verifier.wrapInput(entry.getName(), zf.getStream(entry))) {
					IOUtil.read(in);
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}

			System.out.println("文件验证完成，有问题的文件已在上方列出");
		}
	}
}