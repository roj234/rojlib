package roj.crypt.jar;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFile;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.net.mss.X509CertFormat;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * @author Roj234
 * @since 2024/3/22 0022 19:44
 */
public class JarVerifier {
	public static String CREATED_BY = "ImpLib/JarSigner (v1.1)";
	private static final MyHashSet<String> VALID_CERTIFICATE_EXTENSION = new MyHashSet<>("rsa", "dsa", "ec");
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
		try {
			X509TrustManager tm = X509CertFormat.getDefault();
			tm.checkServerTrusted(certs.toArray(new X509Certificate[0]), "UNKNOWN");
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

		Signature sign = Signature.getInstance(block.getSignatureAlg());
		sign.initVerify(block.getSigner().getPublicKey());
		sign.update(sb);
		if (!sign.verify(block.getSignature())) throw new SecurityException("元签名校验失败");

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
	private static MessageDigest digesterLazyReuse(Map<String, MessageDigest> digests, String algorithm) throws NoSuchAlgorithmException {
		MessageDigest digest = digests.remove(algorithm);
		if (digest == null) digest = MessageDigest.getInstance(algorithm);
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

		var digests = DIGESTS.get();
		if (digests == null) {
			digests = new MyHashMap<>();
			DIGESTS.set(digests);
		}

		MessageDigest md;
		try {
			md = digesterLazyReuse(digests, algorithm);
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
			DIGESTS.get().put(md.getAlgorithm(), md);
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

	@Nullable
	public static String signJar(ZipArchive zf, String hashAlg, String signHashAlg, List<Certificate> certs, PrivateKey prk, String sfName) throws GeneralSecurityException, IOException {
		String signAlg = certs.get(0).getPublicKey().getAlgorithm();
		var signer = Signature.getInstance(signHashAlg.replace("-", "")+"with"+(signAlg.equals("EC")?"ECDSA": signAlg));

		if (!VALID_CERTIFICATE_EXTENSION.contains(signAlg.toLowerCase(Locale.ROOT))) return "Invalid SignAlg: not in "+VALID_CERTIFICATE_EXTENSION;
		if (!SECURE_HASH_ALGORITHMS.contains(hashAlg) || !SECURE_HASH_ALGORITHMS.contains(signHashAlg)) return "Invalid HashAlg: not in "+SECURE_HASH_ALGORITHMS;

		var md = MessageDigest.getInstance(hashAlg);

		byte[] buf = new byte[1024];
		var mfin = zf.getStream("META-INF/MANIFEST.MF");
		if (mfin == null) return "未找到MANIFEST.MF";
		var mf = new Manifest(mfin);

		var digestKey = new Attributes.Name(hashAlg+"-Digest");
		for (ZEntry entry : zf.entries()) {
			if (entry.getName().startsWith("META-INF/")) {
				if (entry.getName().equals("META-INF/MANIFEST.MF")) continue;
				if (VALID_CERTIFICATE_EXTENSION.contains(IOUtil.extensionName(entry.getName()))) {
					String oldSfName = "META-INF/"+IOUtil.fileName(entry.getName())+".SF";
					zf.put(entry.getName(), null);
					zf.put(oldSfName, null);
					mf.getEntries().remove(entry.getName());
					mf.getEntries().remove(oldSfName);
					continue;
				}
				if (entry.getName().endsWith(".SF")) continue;
			}

			var subattr = mf.getAttributes(entry.getName());
			if (subattr == null) {
				subattr = new Attributes(1);
				mf.getEntries().put(entry.getName(), subattr);
			}

			try (var in = zf.getStream(entry)) {
				while (true) {
					int r = in.read(buf);
					if (r < 0) break;
					md.update(buf, 0, r);
				}

				var digest = Base64.encode(DynByteBuf.wrap(md.digest()), IOUtil.getSharedCharBuf()).toString();
				subattr.put(digestKey, digest);
			}
		}

		var ob = IOUtil.getSharedByteBuf();
		mf.write(ob);
		byte[] mfBytes = ob.toByteArray();
		zf.put("META-INF/MANIFEST.MF", new ByteList(mfBytes));

		var mb = new ManifestBytes(mfBytes);

		var sf = new Manifest();
		var sfMain = sf.getMainAttributes();
		sfMain.put(Attributes.Name.SIGNATURE_VERSION, "1.0");
		sfMain.put(new Attributes.Name("Created-By"), CREATED_BY);
		sfMain.put(new Attributes.Name(hashAlg +"-Digest-Manifest"), Base64.encode(DynByteBuf.wrap(md.digest(mfBytes)), IOUtil.getSharedCharBuf()).toString());
		sfMain.put(new Attributes.Name(hashAlg +"-Digest-Manifest-Main-Attributes"), Base64.encode(DynByteBuf.wrap(mb.digest(md, null)), IOUtil.getSharedCharBuf()).toString());
		for (String name : mb.namedAttrMap.keySet()) {
			var attr = new Attributes(1);
			attr.put(digestKey, Base64.encode(DynByteBuf.wrap(mb.digest(md, name)), IOUtil.getSharedCharBuf()).toString());
			sf.getEntries().put(name, attr);
		}

		ob.clear();
		sf.write(ob);
		byte[] sfBytes = ob.toByteArray();
		zf.put("META-INF/"+sfName+".SF", DynByteBuf.wrap(sfBytes));

		signer.initSign(prk);
		signer.update(sfBytes);
		byte[] signature = signer.sign();

		var sb = new SignatureBlock(certs, signature, signer.getAlgorithm(), 0, null);
		zf.put("META-INF/"+sfName+"."+signAlg, DynByteBuf.wrap(sb.getEncoded("PKCS7")));
		return null;
	}

	public static void main(String[] args) throws Exception {
		try (var zf = new ZipFile(args[0])) {
			JarVerifier verifier = create(zf);
			if (verifier == null) {
				System.out.println("文件没有清单属性");
				return;
			}
			if (!verifier.isSigned()) {
				System.out.println("文件没有签名");
				return;
			}

			System.out.println("清单和元签名校验通过");
			System.out.println("是自签证书:"+!verifier.isSignTrusted());
			System.out.println("签名算法:"+verifier.block.getSignatureAlg());
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