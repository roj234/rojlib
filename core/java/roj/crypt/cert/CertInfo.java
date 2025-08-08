package roj.crypt.cert;

import org.jetbrains.annotations.Nullable;
import roj.collect.ArrayList;
import roj.collect.LinkedHashMap;
import roj.crypt.asn1.KnownOID;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.Extension;
import java.util.List;

/**
 * @author Roj234
 * @since 2025/09/07 08:45
 */
public final class CertInfo {
	public final KeyPair key;
	public @Nullable BigInteger serialNumber;
	public LinkedHashMap<KnownOID, String> DN = new LinkedHashMap<>();
	public String signatureAlgorithm = "SHA256";
	public long notBefore = System.currentTimeMillis();
	public long notAfter = notBefore + 3650 * 86400000L;
	public List<Extension> extensions = new ArrayList<>();

	// 证书里一定要有密钥，不是么
	public CertInfo(KeyPair key) {this.key = key;}

	public void setCommonName(String commonName) {DN.put(KnownOID.CommonName, commonName);}

	public void setupCA() {
		extensions.add(CertExtension.basicConstraints(true, 0));
		extensions.add(CertExtension.keyUsage(CertExtension.digitalSignature | CertExtension.nonRepudiation | CertExtension.cRLSign));
	}

	public void setupCodeSigning() {
		extensions.add(CertExtension.basicConstraints(false, 1));
		extensions.add(CertExtension.keyUsage(CertExtension.digitalSignature));
		extensions.add(CertExtension.extendedKeyUsage(KnownOID.codeSigning));
	}

	public void setupHttps(String... domains) {
		extensions.add(CertExtension.basicConstraints(false, 1));
		extensions.add(CertExtension.keyUsage(CertExtension.digitalSignature));
		extensions.add(CertExtension.extendedKeyUsage(KnownOID.serverAuth, KnownOID.clientAuth));
		extensions.add(CertExtension.dnsName(domains));
	}
}
