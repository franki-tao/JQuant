package jquant.experimental.volatility;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;

/**
 * Lazily loaded absorption probability table used by the no-arbitrage SABR
 * model.
 *
 * <p>The table is the official QuantLib {@code sabrabsprob[1209600]} array
 * (Copyright (C) 2014 Peter Caspers, see the README next to the resource)
 * stored as a gzip-compressed little-endian {@code uint32} array.</p>
 *
 * <p>The loader is lazy (class-initialization holder idiom, so it is
 * thread-safe and runs once) and verifies the length, the sum checksum and
 * the SHA-256 of the uncompressed bytes before the table may be used.</p>
 */
final class SabrAbsProbTable {
    static final int EXPECTED_LENGTH = 1_209_600;
    static final long EXPECTED_SUM = 647_857_029_164L;
    static final String EXPECTED_SHA256 =
            "e229402f47e990416e60459b0f7a33748d8bbb80a1312bbca8b2d874a7ad7f9c";

    private static final String RESOURCE =
            "/jquant/experimental/volatility/sabrabsprob.u32.gz";

    private SabrAbsProbTable() {
    }

    private static final class Holder {
        private static final int[] DATA = load();
    }

    static int[] data() {
        return Holder.DATA;
    }

    private static int[] load() {
        byte[] raw;
        try {
            raw = readAll();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "unable to load no-arbitrage SABR absorption probability table " +
                            RESOURCE, e);
        }

        if (raw.length != 4 * EXPECTED_LENGTH) {
            throw new IllegalStateException(
                    "no-arbitrage SABR absorption probability table has " + raw.length +
                            " bytes, expected " + (4 * EXPECTED_LENGTH));
        }

        String digest = sha256(raw);
        if (!EXPECTED_SHA256.equals(digest)) {
            throw new IllegalStateException(
                    "no-arbitrage SABR absorption probability table SHA-256 is " +
                            digest + ", expected " + EXPECTED_SHA256);
        }

        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int[] data = new int[EXPECTED_LENGTH];
        long sum = 0L;
        for (int i = 0; i < EXPECTED_LENGTH; ++i) {
            int value = buffer.getInt();
            data[i] = value;
            sum += value & 0xFFFFFFFFL;
        }

        if (sum != EXPECTED_SUM) {
            throw new IllegalStateException(
                    "no-arbitrage SABR absorption probability table checksum is " +
                            sum + ", expected " + EXPECTED_SUM);
        }
        return data;
    }

    private static byte[] readAll() throws IOException {
        InputStream stream = SabrAbsProbTable.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException(
                    "resource not found on classpath: " + RESOURCE);
        }
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(stream))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4 * EXPECTED_LENGTH);
            byte[] chunk = new byte[8192];
            int read;
            while ((read = in.read(chunk)) != -1)
                out.write(chunk, 0, read);
            return out.toByteArray();
        }
    }

    private static String sha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder builder = new StringBuilder(2 * hash.length);
            for (byte b : hash)
                builder.append(String.format("%02x", b & 0xff));
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
