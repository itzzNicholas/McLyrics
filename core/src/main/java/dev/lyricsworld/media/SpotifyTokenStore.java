package dev.lyricsworld.media;

import dev.lyricsworld.util.Log;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;

/**
 * Stores the Spotify refresh token only on this machine, AES-256-GCM encrypted.
 *
 * The key is derived (PBKDF2) from machine/user specific values plus a random per-file salt.
 * This is "at rest" protection against casual copying of the file (e.g. a config folder shared
 * by accident); it is NOT protection against malware already running as the same user. No OS
 * keystore is used because none is reachable from plain Java 8 on every platform.
 */
public final class SpotifyTokenStore {
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private final Path file;

    public SpotifyTokenStore(Path file) { this.file = file; }

    public synchronized void save(String refreshToken) {
        try {
            SecureRandom rnd = new SecureRandom();
            byte[] salt = new byte[SALT_LEN];
            byte[] iv = new byte[IV_LEN];
            rnd.nextBytes(salt);
            rnd.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(salt), new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(refreshToken.getBytes(StandardCharsets.UTF_8));
            ByteBuffer bb = ByteBuffer.allocate(SALT_LEN + IV_LEN + enc.length);
            bb.put(salt).put(iv).put(enc);
            Files.createDirectories(file.getParent());
            Files.write(file, bb.array());
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // Windows: ACLs of the user profile already restrict access
            }
        } catch (Exception e) {
            Log.warn("Cannot store Spotify token", e);
        }
    }

    public synchronized String load() {
        try {
            if (!Files.exists(file)) return null;
            byte[] all = Files.readAllBytes(file);
            if (all.length <= SALT_LEN + IV_LEN) return null;
            byte[] salt = new byte[SALT_LEN];
            byte[] iv = new byte[IV_LEN];
            ByteBuffer bb = ByteBuffer.wrap(all);
            bb.get(salt).get(iv);
            byte[] enc = new byte[bb.remaining()];
            bb.get(enc);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(salt), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(enc), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.warn("Stored Spotify token unreadable (moved to another machine?) - please reconnect");
            return null;
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            Log.warn("Cannot delete token file", e);
        }
    }

    private static SecretKey key(byte[] salt) throws Exception {
        String material = System.getProperty("user.name", "") + "|" + System.getProperty("user.home", "")
                + "|" + System.getProperty("os.name", "") + "|" + hostName();
        SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] k = f.generateSecret(new PBEKeySpec(material.toCharArray(), salt, 60000, 256)).getEncoded();
        return new SecretKeySpec(k, "AES");
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
