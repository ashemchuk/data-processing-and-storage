package ru.ashemchuk;

import ru.ashemchuk.generation.CertificateGenerator;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import ru.ashemchuk.generation.CryptoIdentity;

final class TestIdentities {
    static CryptoIdentity create() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        KeyPair keys = rsa.generateKeyPair();
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair issuer = ec.generateKeyPair();
        return new CryptoIdentity(keys.getPrivate(), new CertificateGenerator().generate(
                "Anna", keys.getPublic(), issuer.getPrivate(), "Lab CA"));
    }

    static CryptoIdentity withLargeCertificate(CryptoIdentity identity) throws Exception {
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp384r1"));
        KeyPair issuer = ec.generateKeyPair();
        java.time.Instant now = java.time.Instant.now();
        var builder = new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                new org.bouncycastle.asn1.x500.X500Name("CN=Lab CA"),
                java.math.BigInteger.ONE, java.util.Date.from(now),
                java.util.Date.from(now.plusSeconds(3600)),
                new org.bouncycastle.asn1.x500.X500Name("CN=Anna"), identity.cert().getPublicKey());
        builder.addExtension(new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.3.6.1.4.1.55555.1"),
                false, new org.bouncycastle.asn1.DEROctetString(new byte[4 * 1024 * 1024]));
        var signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA384withECDSA")
                .build(issuer.getPrivate());
        return new CryptoIdentity(identity.key(),
                new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                        .getCertificate(builder.build(signer)));
    }
}
