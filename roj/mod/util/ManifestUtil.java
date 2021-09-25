/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.mod.util;

import roj.io.MutableZipFile;
import roj.ui.UIUtil;
import roj.util.ByteList;
import sun.security.util.ManifestDigester;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Attributes.Name;
import java.util.jar.Manifest;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/9/25 19:14
 */
public class ManifestUtil {
    public final Manifest mf;

    private String            alias;
    private Signature     signAlg;
    private X509Certificate[] certChain;

    public ManifestUtil() throws IOException {
        this(null);
    }

    public ManifestUtil(ByteList data) throws IOException {
        this.mf = new Manifest();
        if(data != null && data.pos() > 0)
            this.mf.read(data.asInputStream());
        init();
    }

    private void init() {
        Attributes attr = mf.getMainAttributes();
        if (attr.getValue(Name.MANIFEST_VERSION) == null) {
            attr.put(Name.MANIFEST_VERSION, "1.0");
        }
        final Name CREATED_BY = new Name("Created-By");
        if (attr.getValue(CREATED_BY) == null) {
            String javaVendor = System.getProperty("java.vendor");
            String jdkVersion = System.getProperty("java.version");
            attr.put(CREATED_BY, "Roj234's FastModDev(FMD) on " +
                    jdkVersion + " (" + javaVendor + ")");
        }
        try {
            loadKeyStore("server", new FileInputStream("server.ks"), "123456".toCharArray());
        } catch (IOException | GeneralSecurityException e) {
            e.printStackTrace();
        }
    }

    public void loadKeyStore(String alias, InputStream pkIn, char[] passwd) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = pkIn) {
            ks.load(in, passwd);
        }

        Certificate[] chain = ks.getCertificateChain(alias);
        X509Certificate[] certChain = this.certChain = new X509Certificate[chain.length];
        for(int i = 0; i < chain.length; i++) {
            if (!(chain[i] instanceof X509Certificate)) {
                throw new GeneralSecurityException("Non X-509 Certificate in certificate chain");
            }

            certChain[i] = (X509Certificate)chain[i];
        }
        Key privKey;
        try {
            privKey = ks.getKey(alias, passwd);
        } catch (UnrecoverableKeyException e) {
            passwd = UIUtil.readPassword();
            privKey = ks.getKey(alias, passwd);
        }

        if (!(privKey instanceof PrivateKey)) {
            throw new GeneralSecurityException("Key is not a Private Key");
        } else {
            Signature sig = Signature.getInstance(getSignatureAlg(privKey));
            sig.initSign((PrivateKey) privKey);
            this.signAlg = sig;
            this.alias = alias;
        }
    }

    private static String getSignatureAlg(Key pk) {
        String name = pk.getAlgorithm();
        if (name.equalsIgnoreCase("DSA")) {
            return "SHA256withDSA";
        } else if (name.equalsIgnoreCase("RSA")) {
            return "SHA256withRSA";
        } else if (name.equalsIgnoreCase("EC")) {
            return "SHA256withECDSA";
        } else {
            throw new RuntimeException("Private Key is not DSA nor RSA");
        }
    }

    public void sign(String file, ByteList data) {

    }

    public void doFinal(MutableZipFile mz) throws IOException, GeneralSecurityException {
        String alias = "SIGNFILE";

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");

        Manifest sf = new Manifest();
        Attributes attr = sf.getMainAttributes();
        attr.putValue(Attributes.Name.SIGNATURE_VERSION.toString(), "1.0");

        ByteList bl = new ByteList();
        mf.write(bl.asOutputStream());
        ManifestDigester md = new ManifestDigester(bl.toByteArray());

        ManifestDigester.Entry main = md.get("Manifest-Main-Attributes", false);
        if (main != null) {
            attr.putValue(sha256.getAlgorithm() + "-Digest-" + "Manifest-Main-Attributes", Base64.getEncoder().encodeToString(main.digest(sha256)));
            Map<String, Attributes> sfEnt = sf.getEntries();
            Iterator<Map.Entry<String, Attributes>> mfEnt = mf.getEntries().entrySet().iterator();
            while (mfEnt.hasNext()) {
                String k = mfEnt.next().getKey();
                ManifestDigester.Entry v = md.get(k, false);
                if (v != null) {
                    Attributes dg = new Attributes();
                    dg.putValue(sha256.getAlgorithm() + "-Digest", Base64.getEncoder().encodeToString(v.digest(sha256)));
                    sfEnt.put(k, dg);
                }
            }
            return;
        }

        bl.clear();
        sf.write(bl.asOutputStream());
        mz.setFileData("META-INF/" + alias + ".SF", bl);
        //mz.setFileData("META-INF/" + alias + ".DSA", );
        //signAlg.update();
        //signAlg.sign();
    }
}
