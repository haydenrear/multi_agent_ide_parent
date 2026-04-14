package com.hayden.utilitymodule.security;

import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

@UtilityClass
@Slf4j
public class SignatureUtil {

    public record SignatureErr(String getMessage) implements SingleError {

        public SignatureErr(Throwable message) {
            this(SingleError.parseStackTraceToString(message));
        }
    }

    public static Result<byte[], SignatureErr> takeMessageDigest(byte[] input) {
        return takeMessageDigest(input, (String) null);
    }

    public static Result<byte[], SignatureErr> takeMessageDigest(Path input, @Nullable String algorithm) {
        return retrieveDigestAlgorithm(algorithm)
                .flatMapResult(s -> takeMessageDigest(input, s));
    }

    public static Result<byte[], SignatureErr> takeMessageDigest(Path input, MessageDigest digest) {
        try (InputStream is = Files.newInputStream(input);
             DigestInputStream ds = new DigestInputStream(is, digest)) {
            return Result.ok(ds.getMessageDigest().digest());
        } catch (IOException e) {
            log.error("Could not create digest input stream: {}.", e.getMessage());
            return Result.err(new SignatureErr(e));
        }
    }

    public static @NotNull String hashToString(String toHash) throws NoSuchAlgorithmException {
        return hashToString(toHash, retrieveDigest()) ;
    }

    public static @NotNull String hashToString(String toHash, MessageDigest digest) {
        return hashToString(toHash.getBytes(StandardCharsets.UTF_8), digest);
    }

    public static @NotNull String hashToString(String toHash, MessageDigest digest, Charset charsets) {
        return hashToString(toHash.getBytes(charsets), digest);
    }

    public static @NotNull String hashToString(byte[] toHash, MessageDigest digest) {
        byte[] dig = digest.digest(toHash);
        return Base64.getEncoder().encodeToString(dig);
    }

    public static @NotNull byte[] hashToBytes(byte[] toHash, MessageDigest digest) {
        return digest.digest(toHash);
    }

    public static Result<byte[], SignatureErr> takeMessageDigest(byte[] input, @Nullable String algorithm) {
        var retrieveDigestAlgorithm = retrieveDigestAlgorithm(algorithm);
        return retrieveDigestAlgorithm
                .flatMapResult(md5Algorithm -> {
                    try {
                        byte[] digest = MessageDigest.getInstance(md5Algorithm).digest(input);
                        return Result.ok(digest);
                    } catch (NoSuchAlgorithmException e) {
                        log.error("Error attempting to get {}: {}.", md5Algorithm, e.getMessage());
                        return Result.err(new SignatureErr(e));
                    }
                });
    }

    public static MessageDigest retrieveDigest() throws NoSuchAlgorithmException {

        Result<String, SignatureErr> stringSignatureErrResult = retrieveDigestAlgorithm();

        if (stringSignatureErrResult.isErr()) {
            return MessageDigest.getInstance("MD5");
        }

        return MessageDigest.getInstance(stringSignatureErrResult.r().get());

    }

    public static Result<String, SignatureErr> retrieveDigestAlgorithm() {
        return retrieveDigestAlgorithm(null) ;
    }

    @NotNull
    public static Result<String, SignatureErr> retrieveDigestAlgorithm(@Nullable String algorithm) {
        Set<String> messageDigestAlgorithms = Security.getAlgorithms(MessageDigest.class.getSimpleName());
        Optional<String> retrieveDigestAlgorithm = messageDigestAlgorithms
                .stream().filter(a -> a.equals(algorithm))
                .findAny()
                .or(() -> messageDigestAlgorithms.stream().filter(s -> s.equals("MD5"))
                        .findAny());
        return Result.fromOpt(retrieveDigestAlgorithm);
    }

}
