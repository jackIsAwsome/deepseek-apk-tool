package com.apktool.helper.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class JavaApkSigner {

    public static void sign(Path unsignedApk, Path outputApk, File keystore,
                           String storePass, String keyAlias, String keyPass) throws Exception {
        KeyStore ks;
        if (keystore.exists()) {
            try {
                ks = loadKeystore(keystore, storePass);
            } catch (Exception e) {
                // Unsupported format (e.g. JKS on Android 28+), auto-generate PKCS12
                ks = generateKeystore(keystore, storePass, keyAlias);
            }
        } else {
            ks = generateKeystore(keystore, storePass, keyAlias);
        }
        PrivateKey privateKey = (PrivateKey) ks.getKey(keyAlias, keyPass.toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(keyAlias);

        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        Map<String, String> fileDigests = new TreeMap<>();

        // First pass: compute SHA-256 of each file's uncompressed content
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(unsignedApk))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("META-INF/") || name.endsWith("/")) continue;
                byte[] data = readAll(zis);
                fileDigests.put(name, Base64.getEncoder().encodeToString(sha256.digest(data)));
            }
        }

        // Build MANIFEST.MF manually with CRLF (java.util.jar.Manifest uses LF on Android)
        StringBuilder mf = new StringBuilder();
        mf.append("Manifest-Version: 1.0\r\n");
        mf.append("Created-By: APK Tool (Android)\r\n\r\n");

        for (Map.Entry<String, String> e : fileDigests.entrySet()) {
            mf.append("Name: ").append(e.getKey()).append("\r\n");
            mf.append("SHA-256-Digest: ").append(e.getValue()).append("\r\n\r\n");
        }
        byte[] mfContent = mf.toString().getBytes(StandardCharsets.UTF_8);

        // Build CERT.SF with CRLF
        // Per JAR spec: CERT.SF entry digest = SHA-256 of the corresponding MANIFEST.MF entry lines
        StringBuilder sf = new StringBuilder();
        sf.append("Signature-Version: 1.0\r\n");
        sf.append("Created-By: 1.0 (APK Tool Android)\r\n");
        sf.append("SHA-256-Digest-Manifest: ");
        sf.append(Base64.getEncoder().encodeToString(sha256.digest(mfContent)));
        sf.append("\r\n\r\n");

        for (Map.Entry<String, String> e : fileDigests.entrySet()) {
            String mfEntry = "Name: " + e.getKey() + "\r\nSHA-256-Digest: " + e.getValue() + "\r\n\r\n";
            String sfDigest = Base64.getEncoder().encodeToString(sha256.digest(mfEntry.getBytes(StandardCharsets.UTF_8)));
            sf.append("Name: ").append(e.getKey()).append("\r\n");
            sf.append("SHA-256-Digest: ").append(sfDigest).append("\r\n\r\n");
        }
        byte[] sfContent = sf.toString().getBytes(StandardCharsets.UTF_8);

        // Sign CERT.SF with private key
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey);
        sig.update(sfContent);
        byte[] signatureBytes = sig.sign();

        // Build PKCS#7 SignedData
        byte[] certRsa = buildPkcs7(cert, signatureBytes, sha256);

        // Write signed APK
        Files.createDirectories(outputApk.getParent());
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(unsignedApk));
             ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputApk.toFile()))) {

            // Copy all non-META-INF entries from unsigned APK
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().startsWith("META-INF/")) continue;
                ZipEntry outEntry = new ZipEntry(entry.getName());
                zos.putNextEntry(outEntry);
                byte[] data = readAll(zis);
                zos.write(data);
                zos.closeEntry();
                zis.closeEntry();
            }

            // Add META-INF signature entries (STORED method for correctness)
            writeZipEntry(zos, "META-INF/MANIFEST.MF", mfContent);
            writeZipEntry(zos, "META-INF/CERT.SF", sfContent);
            writeZipEntry(zos, "META-INF/CERT.RSA", certRsa);

            zos.finish();
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static KeyStore loadKeystore(File keystore, String storePass) throws Exception {
        // Android supports PKCS12 natively; JKS was removed in newer versions
        String[] types = {KeyStore.getDefaultType(), "PKCS12", "JKS"};
        for (String type : types) {
            try {
                KeyStore ks = KeyStore.getInstance(type);
                try (FileInputStream fis = new FileInputStream(keystore)) {
                    ks.load(fis, storePass.toCharArray());
                }
                return ks;
            } catch (Exception e) {
                // try next type
            }
        }
        throw new Exception("Cannot load keystore: unsupported format (tried default, PKCS12, JKS)");
    }

    private static KeyStore generateKeystore(File keystore, String password, String keyAlias) throws Exception {
        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        java.security.KeyPair kp = kpg.generateKeyPair();

        X509Certificate cert = generateSelfSignedCert(kp, "CN=APK Tool,O=APK Tool,C=US");

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry(keyAlias, kp.getPrivate(), password.toCharArray(),
                       new java.security.cert.Certificate[]{cert});

        keystore.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(keystore)) {
            ks.store(fos, password.toCharArray());
        }
        return ks;
    }

    private static X509Certificate generateSelfSignedCert(java.security.KeyPair kp, String dn) throws Exception {
        // Build X.509 certificate using pure DER encoding (no hidden API dependencies)
        java.security.SecureRandom random = new java.security.SecureRandom();
        java.math.BigInteger serial = new java.math.BigInteger(64, random);

        java.util.Date notBefore = new java.util.Date();
        java.util.Date notAfter = new java.util.Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000);

        // Extract public key components
        java.security.interfaces.RSAPublicKey rsaPub = (java.security.interfaces.RSAPublicKey) kp.getPublic();
        byte[] pubKeyEncoded = encodeRsaPublicKey(rsaPub.getModulus(), rsaPub.getPublicExponent());

        // DER OIDs
        byte[] rsaOid = derOid("1.2.840.113549.1.1.1");      // rsaEncryption
        byte[] sha256RsaOid = derOid("1.2.840.113549.1.1.11"); // sha256WithRSAEncryption

        // SubjectPublicKeyInfo
        byte[] spki = derSequence(
            derSequence(rsaOid, derNull()),
            derBitString(pubKeyEncoded)
        );

        // Validity
        byte[] validity = derSequence(
            derUtcTime(notBefore),
            derUtcTime(notAfter)
        );

        // Issuer and Subject DN
        byte[] name = encodeX500Name(new String[][]{
            {"2.5.4.6", "US"},    // C
            {"2.5.4.10", "APK Tool"}, // O
            {"2.5.4.3", "APK Tool Auto"} // CN
        });

        // TBSCertificate
        byte[] version = derContextTag(0, derInteger(java.math.BigInteger.valueOf(2))); // v3
        byte[] tbsCert = derSequence(
            version,
            derInteger(serial),
            derSequence(sha256RsaOid, derNull()), // signature algorithm
            name,  // issuer
            validity,
            name,  // subject
            spki
        );

        // Sign TBSCertificate
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(tbsCert);
        byte[] signatureBytes = sig.sign();

        // Build complete certificate
        byte[] certDer = derSequence(
            tbsCert,
            derSequence(sha256RsaOid, derNull()),
            derBitString(signatureBytes)
        );

        // Parse with CertificateFactory
        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(new java.io.ByteArrayInputStream(certDer));
    }

    private static byte[] encodeRsaPublicKey(java.math.BigInteger modulus, java.math.BigInteger exponent) {
        return derSequence(
            derInteger(modulus),
            derInteger(exponent)
        );
    }

    private static byte[] encodeX500Name(String[][] rdnEntries) {
        // RDN SET for each attribute
        byte[][] rdns = new byte[rdnEntries.length][];
        for (int i = 0; i < rdnEntries.length; i++) {
            byte[] attrTypeAndValue = derSequence(
                derOid(rdnEntries[i][0]),
                derUtf8String(rdnEntries[i][1])
            );
            rdns[i] = derSet(attrTypeAndValue);
        }
        return derSequence(rdns);
    }

    private static byte[] derBitString(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x03);
        int len = data.length + 1;
        encodeLength(out, len);
        out.write(0x00); // unused bits
        try { out.write(data); } catch (IOException ignored) {}
        return out.toByteArray();
    }

    private static byte[] derUtf8String(String s) {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        return derTag(0x0C, data);
    }

    private static byte[] derUtcTime(java.util.Date date) {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyMMddHHmmss'Z'");
        fmt.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String s = fmt.format(date);
        return derTag(0x17, s.getBytes(StandardCharsets.US_ASCII));
    }

    private static void encodeLength(ByteArrayOutputStream out, int len) {
        if (len < 128) {
            out.write(len);
        } else if (len < 256) {
            out.write(0x81);
            out.write(len);
        } else {
            out.write(0x82);
            out.write(len >> 8);
            out.write(len & 0xFF);
        }
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] tmp = new byte[8192];
        int n;
        while ((n = is.read(tmp)) != -1) buf.write(tmp, 0, n);
        return buf.toByteArray();
    }

    private static byte[] buildPkcs7(X509Certificate cert, byte[] signature, MessageDigest sha256) throws Exception {
        byte[] certEncoded = cert.getEncoded();
        String sigAlgOid = "1.2.840.113549.1.1.1"; // RSA
        String digestOid = "2.16.840.1.101.3.4.2.1"; // SHA-256
        BigInteger serial = cert.getSerialNumber();
        byte[] issuerEncoded = cert.getIssuerX500Principal().getEncoded();

        // IssuerAndSerialNumber
        byte[] issuerAndSerial = derSequence(
            issuerEncoded,
            derInteger(serial)
        );

        // SignerInfo
        byte[] signerInfo = derSequence(
            derInteger(BigInteger.ONE),
            issuerAndSerial,
            derSequence(derOid(digestOid), derNull()),
            derSequence(derOid(sigAlgOid), derNull()),
            derOctetString(signature)
        );

        // certificates [0] IMPLICIT SET OF Certificate
        byte[] certSet = derSet(certEncoded);
        byte[] implicitCerts = derContextTag(0, certSet);

        // SignedData
        byte[] signedData = derSequence(
            derInteger(BigInteger.ONE),
            derSet(derSequence(derOid(digestOid), derNull())),
            derSequence(derOid("1.2.840.113549.1.7.1"), derNull()), // contentInfo = data
            implicitCerts,
            derSet(signerInfo)
        );

        // ContentInfo wrapping SignedData
        byte[] contentInfo = derSequence(
            derOid("1.2.840.113549.1.7.2"), // signedData OID
            derContextTag(0, signedData)
        );

        return contentInfo;
    }

    // Minimal DER encoding helpers
    private static byte[] derInteger(BigInteger val) {
        byte[] b = val.toByteArray();
        return derTag(0x02, b);
    }

    private static byte[] derOid(String oid) {
        String[] parts = oid.split("\\.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]));
        for (int i = 2; i < parts.length; i++) {
            long v = Long.parseLong(parts[i]);
            if (v < 128) {
                out.write((int) v);
            } else {
                byte[] tmp = new byte[8];
                int idx = tmp.length;
                tmp[--idx] = (byte) (v & 0x7F);
                v >>= 7;
                while (v > 0) {
                    tmp[--idx] = (byte) ((v & 0x7F) | 0x80);
                    v >>= 7;
                }
                out.write(tmp, idx, tmp.length - idx);
            }
        }
        return derTag(0x06, out.toByteArray());
    }

    private static byte[] derNull() {
        return derTag(0x05, new byte[0]);
    }

    private static byte[] derOctetString(byte[] data) {
        return derTag(0x04, data);
    }

    private static byte[] derSequence(byte[]... parts) {
        return derTag(0x30, concat(parts));
    }

    private static byte[] derSet(byte[]... parts) {
        return derTag(0x31, concat(parts));
    }

    private static byte[] derContextTag(int num, byte[] content) {
        return derTag(0xA0 | num, content);
    }

    private static byte[] derTag(int tag, byte[] content) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = content.length;
        if (len < 128) {
            out.write(len);
        } else if (len < 256) {
            out.write(0x81);
            out.write(len);
        } else {
            out.write(0x82);
            out.write(len >> 8);
            out.write(len & 0xFF);
        }
        out.write(content, 0, len);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] a : arrays) {
            try { out.write(a); } catch (IOException ignored) {}
        }
        return out.toByteArray();
    }
}
