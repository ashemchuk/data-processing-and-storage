package ru.ashemchuk;

import ru.ashemchuk.generation.CertificateGenerator;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;

import static org.junit.jupiter.api.Assertions.*;

class CertificateGeneratorTest {
    private static KeyPair clientKeys;
    private static KeyPair issuerKeys;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        clientKeys = rsa.generateKeyPair();

        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp384r1"));
        issuerKeys = ec.generateKeyPair();
    }

    @Test
    void createsValidCertificateForClientKey() throws Exception {
        X509Certificate certificate = new CertificateGenerator().generate(
                "Anna", clientKeys.getPublic(), issuerKeys.getPrivate(), "Lab CA");

        assertEquals(new X500Principal("CN=Anna"), certificate.getSubjectX500Principal());
        assertEquals(new X500Principal("CN=Lab CA"), certificate.getIssuerX500Principal());
        assertArrayEquals(clientKeys.getPublic().getEncoded(), certificate.getPublicKey().getEncoded());
        assertTrue(certificate.getSerialNumber().signum() > 0, "Serial must be positive");
        assertTrue(certificate.getNotBefore().before(certificate.getNotAfter()));
        certificate.checkValidity();
        certificate.verify(issuerKeys.getPublic());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Anna", "Anna,O=Other", "Anna+Bob=Team"})
    void keepsNamesInSingleCommonNameFields(String name) throws Exception {
        X509Certificate certificate = new CertificateGenerator().generate(
                name, clientKeys.getPublic(), issuerKeys.getPrivate(), name);

        X500Name subject = X500Name.getInstance(certificate.getSubjectX500Principal().getEncoded());
        X500Name issuer = X500Name.getInstance(certificate.getIssuerX500Principal().getEncoded());

        assertEquals(1, subject.getRDNs().length);
        assertEquals(1, issuer.getRDNs().length);
        assertEquals(1, subject.getRDNs(BCStyle.CN).length);
        assertEquals(1, issuer.getRDNs(BCStyle.CN).length);
        assertEquals(name, subject.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());
        assertEquals(name, issuer.getRDNs(BCStyle.CN)[0].getFirst().getValue().toString());
    }
}
