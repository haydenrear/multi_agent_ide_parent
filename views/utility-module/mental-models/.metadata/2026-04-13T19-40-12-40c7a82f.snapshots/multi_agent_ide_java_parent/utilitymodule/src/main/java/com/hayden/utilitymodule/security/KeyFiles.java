package com.hayden.utilitymodule.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

@Component
@Slf4j
public class KeyFiles {

    @Autowired
    private KeyConfigProperties keyConfigProperties;

    public KeyPair getKeyPair() {
        log.info("Initializing key pair.");

        if (!keyConfigProperties.getKeyPath().toFile().exists())
            throw new IllegalStateException("Key file path %s does not exist".formatted(keyConfigProperties.getKeyPath()));

        return ensureRsaKeyPair(keyConfigProperties.getKeyPath(), keyConfigProperties.getKeyName(), 2048);
    }


    public static KeyPair ensureRsaKeyPair(Path dir, String baseName, int bits) {
        try {
            Files.createDirectories(dir);
            Path privPem = dir.resolve(baseName + ".pem");     // PKCS#8 private (PEM)
            Path pubPem  = dir.resolve(baseName + ".pub.pem"); // X.509 public (PEM)

            if (Files.exists(privPem) && Files.exists(pubPem)) {
                log.info("RSA key pair already exists and will be read.");
                return loadKeyPairFromPem(privPem, pubPem);
            }

            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(bits);
            KeyPair kp = kpg.generateKeyPair();

            writePrivateKeyPemAtomically(privPem, kp.getPrivate());
            writePublicKeyPemAtomically(pubPem, kp.getPublic());

            lockDownPermissionsIfPosix(privPem, true);
            lockDownPermissionsIfPosix(pubPem, false);

            return kp;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException("Failed to ensure RSA key pair", e);
        }
    }

    public static KeyPair loadKeyPairFromPem(Path privatePem, Path publicPem) throws Exception {
        log.info("Reading private key.");
        byte[] pkcs8 = readPem(privatePem, "PRIVATE KEY");
        log.info("Reading public key.");
        byte[] spki  = readPem(publicPem,  "PUBLIC KEY");

        log.info("Loading RSA key pair from pem.");

        KeyFactory kf = KeyFactory.getInstance("RSA");
        log.info("Generating private key.");
        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        log.info("Generating public key.");
        PublicKey  pub  = kf.generatePublic(new X509EncodedKeySpec(spki));
        log.info("Returning key pair.");
        return new KeyPair(pub, priv);
    }

    /* --------------------- helpers --------------------- */

    private static void writePrivateKeyPemAtomically(Path path, PrivateKey key) throws IOException {
        writePemAtomically(path, "PRIVATE KEY", key.getEncoded());
    }

    private static void writePublicKeyPemAtomically(Path path, PublicKey key) throws IOException {
        writePemAtomically(path, "PUBLIC KEY", key.getEncoded());
    }

    private static void writePemAtomically(Path path, String type, byte[] der) throws IOException {
        String pem = toPem(type, der);
        Path tmp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
        Files.writeString(tmp, pem, StandardCharsets.US_ASCII, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String toPem(String type, byte[] der) {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                           .encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + b64 + "\n-----END " + type + "-----\n";
    }

    private static byte[] readPem(Path path, String type) throws IOException {
        String all = Files.readString(path, StandardCharsets.US_ASCII);

        String begin = "-----BEGIN " + type + "-----";
        String end   = "-----END " + type + "-----";
        int i = all.indexOf(begin);
        int j = all.indexOf(end);

        if (i < 0 || j < 0)
            throw new IOException("PEM block not found: " + type);

        String base64 = all.substring(i + begin.length(), j).replaceAll("\\s", "");

        var decoded = Base64.getDecoder().decode(base64);
        return decoded;
    }

    private static void lockDownPermissionsIfPosix(Path p, boolean isPrivate) {
        try {
            // Only on POSIX filesystems (Linux/macOS). Windows will throw.
            Set<PosixFilePermission> perms = isPrivate
                                             ? EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                                             : EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.GROUP_READ);
            Files.setPosixFilePermissions(p, perms);
        } catch (UnsupportedOperationException | IOException ignored) {
            // Best effort; on Windows rely on ACLs or container FS defaults.
        }
    }
}
