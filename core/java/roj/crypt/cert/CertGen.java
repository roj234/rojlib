package roj.crypt.cert;

import org.jetbrains.annotations.NotNull;
import roj.crypt.CryptoFactory;
import roj.crypt.asn1.DerReader;
import roj.crypt.asn1.DerValue;
import roj.crypt.asn1.DerWriter;
import roj.crypt.asn1.KnownOID;
import roj.text.DateFormat;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2025/09/07 05:42
 */
public final class CertGen {
	public CertGen() {this(new SecureRandom());}
	public CertGen(SecureRandom srnd) {this.srnd = srnd;}

	private final SecureRandom srnd;
	private static final DateFormat dateFormat = DateFormat.create("YYYY MM DD HH ii ss 'Z'");

	public DerWriter generate(CertInfo subject, @NotNull CertInfo issuer) {return generate(subject, issuer, new DerWriter());}
	public DerWriter generate(CertInfo subject, @NotNull CertInfo issuer, DerWriter dw) {
		PublicKey subjectPublicKey = subject.key.getPublic();
		var keyAlg = subjectPublicKey.getAlgorithm();
		String signAlg = KnownOID.getSignatureAlgorithm(subject.signatureAlgorithm, keyAlg);
		KnownOID signAlgOid = KnownOID.valueOf(signAlg);
		signAlgOid.assertType("signAlg");

		dw.begin(DerValue.SEQUENCE); //begin TBSCertificate

		dw.begin(0xA0); //begin version
		dw.writeInt(BigInteger.TWO);
		dw.end();//end version
		var sn = subject.serialNumber;
		if (sn == null) subject.serialNumber = sn = new BigInteger(128, srnd);
		dw.writeInt(sn);
		dw.begin(DerValue.SEQUENCE); //begin signature
		dw.writeOid(signAlgOid.oid.value);
		dw.writeNull();
		dw.end(); //end signature
		writeDN(issuer, dw); //issuer
		dw.begin(DerValue.SEQUENCE); //begin Validity
		dw.writeText(DerValue.GeneralizedTime, dateFormat.format(subject.notBefore));
		dw.writeText(DerValue.GeneralizedTime, dateFormat.format(subject.notAfter));
		dw.end(); //end Validity
		writeDN(subject, dw); //subject
		//dw.begin(DerValue.SEQUENCE); //begin SubjectPublicKeyInfo
		ByteList subjectPublicKeyInfo = DynByteBuf.wrap(subjectPublicKey.getEncoded());
		dw.write(subjectPublicKeyInfo);
		//dw.end(); //end SubjectPublicKeyInfo
		dw.begin(0xA0 | 3); //begin extensions
		dw.begin(DerValue.SEQUENCE);

		hasSubjectKeyId: {
			for (var extension : subject.extensions) {
				if (extension.getId().equals(KnownOID.SubjectKeyID.name())) {
					break hasSubjectKeyId;
				}
			}
			byte[] digest = CryptoFactory.getSharedDigest("SHA-1").digest(subjectPublicKeyInfo.list);

			var dw1 = new DerWriter();
			dw1.writeBytes(Arrays.copyOf(digest, 20));
			subject.extensions.add(0, new CertExtension(KnownOID.SubjectKeyID, false, dw1.toByteArray()));
		}

		hasAuthorityKeyId:
		if (subject != issuer) {
			for (var extension : subject.extensions) {
				if (extension.getId().equals(KnownOID.AuthorityKeyID.name())) {
					break hasAuthorityKeyId;
				}
			}
			for (var extension : issuer.extensions) {
				if (extension.getId().equals(KnownOID.SubjectKeyID.name())) {
					var dw1 = new DerWriter();
					dw1.begin(DerValue.SEQUENCE);

					var subjectKeyId = DynByteBuf.wrap(extension.getValue());
					subjectKeyId.readUnsignedByte();
					try {
						DerReader.readLength1(subjectKeyId);
					} catch (IOException e) {
						throw new UnsupportedOperationException("扩展格式非法");
					}

					dw1.write(0x80, subjectKeyId); // keyId
					dw1.end();
					subject.extensions.add(0, new CertExtension(KnownOID.AuthorityKeyID, false, dw1.toByteArray()));
					break;
				}
			}
		}

		for (var extension : subject.extensions) {
			dw.begin(DerValue.SEQUENCE);
			dw.writeOid(KnownOID.valueOf(extension.getId()).oid.value);
			if (extension.isCritical()) dw.writeBool(true);
			dw.writeBytes(extension.getValue());
			dw.end();
		}

		dw.end();
		dw.end(); //end extensions

		dw.end(); // end TBSCertificate

		ByteList certificateData = new ByteList();
		dw.flush(certificateData);

		byte[] signature;
		try {
			var sign = Signature.getInstance(signAlg);
			sign.initSign(issuer.key.getPrivate());
			sign.update(certificateData.nioBuffer());
			signature = sign.sign();
		} catch (Exception e) {
			throw new UnsupportedOperationException("签名失败", e);
		}

		dw = new DerWriter();
		dw.begin(DerValue.SEQUENCE); //begin CERTIFICATE

		dw.write(certificateData.slice());

		dw.begin(DerValue.SEQUENCE); //begin signatureAlgorithm
		dw.writeOid(signAlgOid.oid.value);
		dw.writeNull();
		dw.end(); //end signatureAlgorithm

		dw.writeBits(signature, signature.length * 8);

		dw.end(); //end CERTIFICATE
		return dw;
	}

	private static void writeDN(CertInfo info, DerWriter dw) {
		dw.begin(DerValue.SEQUENCE);
		for (var entry : info.DN.entrySet()) {
			entry.getKey().assertType("DN");
			dw.begin(DerValue.SET);
			dw.begin(DerValue.SEQUENCE);
			dw.writeOid(entry.getKey().oid.value);
			String value = entry.getValue();
			dw.writeText(TextUtil.isLatin1(value) ? DerValue.PrintableString : DerValue.UTF8_STRING, value);
			dw.end();
			dw.end();
		}
		dw.end();
	}
}
