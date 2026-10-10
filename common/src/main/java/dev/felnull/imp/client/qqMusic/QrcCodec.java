package dev.felnull.imp.client.qqMusic;

import dev.felnull.imp.music.resource.Lyric;
import it.unimi.dsi.fastutil.floats.Float2ObjectLinkedOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

/**
 * QQ Music QRC ("per-word") lyric codec: decrypt the payload, parse it into {@link Lyric}. A Java port of the MIT-licensed
 * {@code qrc-decoder} (github.com/apoint123/qrc-decoder, (c) 2025 apoint123): not standard DES/3DES, never security-related.
 * Payload = CDATA hex from {@code POST /qqmusic/fcgi-bin/lyric_download.fcg}, zlib QRC text decrypting to an XML shell with a {@code LyricContent} attribute.
 */
public final class QrcCodec {
    private QrcCodec() {
    }

    // ------------------------------------------------------------------
    // Cipher tables (ported verbatim from qrc-decoder / LyricDecoder)
    // ------------------------------------------------------------------

    private static final int[] SBOX1 = {
            14, 4, 13, 1, 2, 15, 11, 8, 3, 10, 6, 12, 5, 9, 0, 7,
            0, 15, 7, 4, 14, 2, 13, 1, 10, 6, 12, 11, 9, 5, 3, 8,
            4, 1, 14, 8, 13, 6, 2, 11, 15, 12, 9, 7, 3, 10, 5, 0,
            15, 12, 8, 2, 4, 9, 1, 7, 5, 11, 3, 14, 10, 0, 6, 13,
    };
    private static final int[] SBOX2 = {
            15, 1, 8, 14, 6, 11, 3, 4, 9, 7, 2, 13, 12, 0, 5, 10,
            3, 13, 4, 7, 15, 2, 8, 15, 12, 0, 1, 10, 6, 9, 11, 5,
            0, 14, 7, 11, 10, 4, 13, 1, 5, 8, 12, 6, 9, 3, 2, 15,
            13, 8, 10, 1, 3, 15, 4, 2, 11, 6, 7, 12, 0, 5, 14, 9,
    };
    private static final int[] SBOX3 = {
            10, 0, 9, 14, 6, 3, 15, 5, 1, 13, 12, 7, 11, 4, 2, 8,
            13, 7, 0, 9, 3, 4, 6, 10, 2, 8, 5, 14, 12, 11, 15, 1,
            13, 6, 4, 9, 8, 15, 3, 0, 11, 1, 2, 12, 5, 10, 14, 7,
            1, 10, 13, 0, 6, 9, 8, 7, 4, 15, 14, 3, 11, 5, 2, 12,
    };
    private static final int[] SBOX4 = {
            7, 13, 14, 3, 0, 6, 9, 10, 1, 2, 8, 5, 11, 12, 4, 15,
            13, 8, 11, 5, 6, 15, 0, 3, 4, 7, 2, 12, 1, 10, 14, 9,
            10, 6, 9, 0, 12, 11, 7, 13, 15, 1, 3, 14, 5, 2, 8, 4,
            3, 15, 0, 6, 10, 10, 13, 8, 9, 4, 5, 11, 12, 7, 2, 14,
    };
    private static final int[] SBOX5 = {
            2, 12, 4, 1, 7, 10, 11, 6, 8, 5, 3, 15, 13, 0, 14, 9,
            14, 11, 2, 12, 4, 7, 13, 1, 5, 0, 15, 10, 3, 9, 8, 6,
            4, 2, 1, 11, 10, 13, 7, 8, 15, 9, 12, 5, 6, 3, 0, 14,
            11, 8, 12, 7, 1, 14, 2, 13, 6, 15, 0, 9, 10, 4, 5, 3,
    };
    private static final int[] SBOX6 = {
            12, 1, 10, 15, 9, 2, 6, 8, 0, 13, 3, 4, 14, 7, 5, 11,
            10, 15, 4, 2, 7, 12, 9, 5, 6, 1, 13, 14, 0, 11, 3, 8,
            9, 14, 15, 5, 2, 8, 12, 3, 7, 0, 4, 10, 1, 13, 11, 6,
            4, 3, 2, 12, 9, 5, 15, 10, 11, 14, 1, 7, 6, 0, 8, 13,
    };
    private static final int[] SBOX7 = {
            4, 11, 2, 14, 15, 0, 8, 13, 3, 12, 9, 7, 5, 10, 6, 1,
            13, 0, 11, 7, 4, 9, 1, 10, 14, 3, 5, 12, 2, 15, 8, 6,
            1, 4, 11, 13, 12, 3, 7, 14, 10, 15, 6, 8, 0, 5, 9, 2,
            6, 11, 13, 8, 1, 4, 10, 7, 9, 5, 0, 15, 14, 2, 3, 12,
    };
    private static final int[] SBOX8 = {
            13, 2, 8, 4, 6, 15, 11, 1, 10, 9, 3, 14, 5, 0, 12, 7,
            1, 15, 13, 8, 10, 3, 7, 4, 12, 5, 6, 11, 0, 14, 9, 2,
            7, 11, 4, 1, 9, 12, 14, 2, 0, 6, 10, 13, 15, 3, 5, 8,
            2, 1, 14, 7, 4, 10, 8, 13, 15, 12, 9, 0, 3, 5, 6, 11,
    };
    private static final int[] P_BOX = {
            16, 7, 20, 21, 29, 12, 28, 17, 1, 15, 23, 26, 5, 18, 31, 10,
            2, 8, 24, 14, 32, 27, 3, 9, 19, 13, 30, 6, 22, 11, 4, 25,
    };
    private static final int[] E_BOX_TABLE = {
            32, 1, 2, 3, 4, 5, 4, 5, 6, 7, 8, 9, 8, 9, 10, 11,
            12, 13, 12, 13, 14, 15, 16, 17, 16, 17, 18, 19, 20, 21, 20, 21,
            22, 23, 24, 25, 24, 25, 26, 27, 28, 29, 28, 29, 30, 31, 32, 1,
    };
    private static final int[] KEY_RND_SHIFT = {
            1, 1, 2, 2, 2, 2, 2, 2, 1, 2, 2, 2, 2, 2, 2, 1,
    };
    private static final int[] KEY_PERM_C = {
            56, 48, 40, 32, 24, 16, 8, 0, 57, 49, 41, 33, 25, 17, 9, 1,
            58, 50, 42, 34, 26, 18, 10, 2, 59, 51, 43, 35,
    };
    private static final int[] KEY_PERM_D = {
            62, 54, 46, 38, 30, 22, 14, 6, 61, 53, 45, 37, 29, 21, 13, 5,
            60, 52, 44, 36, 28, 20, 12, 4, 27, 19, 11, 3,
    };
    private static final int[] KEY_COMPRESSION = {
            13, 16, 10, 23, 0, 4, 2, 27, 14, 5, 20, 9, 22, 18, 11, 3,
            25, 7, 15, 6, 26, 19, 12, 1, 40, 51, 30, 36, 46, 54, 29, 39,
            50, 44, 32, 47, 43, 48, 38, 55, 33, 52, 45, 41, 49, 35, 28, 31,
    };
    private static final int[] IP_RULE = { 34, 42, 50, 58, 2, 10, 18, 26, 36, 44, 52, 60, 4, 12, 20, 28, 38, 46, 54, 62, 6, 14,
            22, 30, 40, 48, 56, 64, 8, 16, 24, 32, 33, 41, 49, 57, 1, 9, 17, 25, 35, 43, 51, 59, 3, 11, 19, 27, 37, 45,
            53, 61, 5, 13, 21, 29, 39, 47, 55, 63, 7, 15, 23, 31 };
    private static final int[] INV_IP_RULE = { 37, 5, 45, 13, 53, 21, 61, 29, 38, 6, 46, 14, 54, 22, 62, 30, 39, 7, 47, 15, 55,
            23, 63, 31, 40, 8, 48, 16, 56, 24, 64, 32, 33, 1, 41, 9, 49, 17, 57, 25, 34, 2, 42, 10, 50, 18, 58, 26, 35,
            3, 43, 11, 51, 19, 59, 27, 36, 4, 44, 12, 52, 20, 60, 28 };

    private static final int[][] SBOXES = { SBOX1, SBOX2, SBOX3, SBOX4, SBOX5, SBOX6, SBOX7, SBOX8 };

    /** The three payload keys, as ASCII (KK1/KK2/KK3 in the original implementation). */
    private static final byte[][] KEYS = {
            "!@#)(*$%".getBytes(StandardCharsets.US_ASCII),
            "123ZXC!@".getBytes(StandardCharsets.US_ASCII),
            "!@#)(NHL".getBytes(StandardCharsets.US_ASCII) };

    private static final int[] IP_L = new int[2048];
    private static final int[] IP_R = new int[2048];
    private static final int[] IIP_L = new int[2048];
    private static final int[] IIP_R = new int[2048];
    private static final int[] SP_TABLE = new int[512];
    private static final int[] EBOX_HIGH = new int[1024];
    private static final int[] EBOX_LOW = new int[1024];

    static {
        for (int bytePos = 0; bytePos < 8; bytePos++) {
            for (int byteVal = 0; byteVal < 256; byteVal++) {
                long input = ((long) byteVal) << (56 - bytePos * 8);
                int idx = (bytePos << 8) | byteVal;
                long p = applyPermutation(input, IP_RULE);
                IP_L[idx] = (int) (p >>> 32);
                IP_R[idx] = (int) p;
                long q = applyPermutation(input, INV_IP_RULE);
                IIP_L[idx] = (int) (q >>> 32);
                IIP_R[idx] = (int) q;
            }
        }
        for (int sBoxIdx = 0; sBoxIdx < 8; sBoxIdx++) {
            for (int sBoxInput = 0; sBoxInput < 64; sBoxInput++) {
                int fourBit = SBOXES[sBoxIdx][calculateSboxIndex(sBoxInput)];
                SP_TABLE[(sBoxIdx << 6) | sBoxInput] = applyQqPboxPermutation(fourBit << (28 - sBoxIdx * 4));
            }
        }
        for (int chunkIdx = 0; chunkIdx < 4; chunkIdx++) {
            int shift = (3 - chunkIdx) * 8;
            for (int byteVal = 0; byteVal < 256; byteVal++) {
                int high24 = 0, low24 = 0;
                int input = byteVal << shift;
                for (int i = 0; i < 24; i++) {
                    if (((input >>> (32 - E_BOX_TABLE[i])) & 1) != 0) high24 |= 1 << (23 - i);
                }
                for (int i = 24; i < 48; i++) {
                    if (((input >>> (32 - E_BOX_TABLE[i])) & 1) != 0) low24 |= 1 << (47 - i);
                }
                int idx = (chunkIdx << 8) | byteVal;
                EBOX_HIGH[idx] = high24;
                EBOX_LOW[idx] = low24;
            }
        }
    }

    private static long permuteFromKeyBytes(byte[] key, int[] table) {
        long out = 0;
        long mask = 1L << (table.length - 1);
        for (int i = 0; i < table.length; i++) {
            int pos = table[i];
            int wordIndex = pos >> 5, bitInWord = pos & 31, byteInWord = bitInWord >> 3, bitInByte = bitInWord & 7;
            int byteIndex = wordIndex * 4 + 3 - byteInWord;
            int bit = (key[byteIndex] >> (7 - bitInByte)) & 1;
            if (bit != 0) out |= mask;
            mask >>= 1;
        }
        return out;
    }

    private static long rotateLeft28(long value, int amount) {
        long val = value & 0xfffffff0L;
        return ((val << amount) | (val >>> (28 - amount))) & 0xfffffff0L;
    }

    /** 16 round keys, each as {high24, low24}. */
    private static int[][] keySchedule(byte[] key, boolean decrypt) {
        int[][] schedule = new int[16][2];
        long c = permuteFromKeyBytes(key, KEY_PERM_C) << 4;
        long d = permuteFromKeyBytes(key, KEY_PERM_D) << 4;
        for (int i = 0; i < 16; i++) {
            c = rotateLeft28(c, KEY_RND_SHIFT[i]);
            d = rotateLeft28(d, KEY_RND_SHIFT[i]);
            int toGen = decrypt ? 15 - i : i;
            long sub = 0;
            for (int k = 0; k < KEY_COMPRESSION.length; k++) {
                int pos = KEY_COMPRESSION[k];
                long bit = pos < 28 ? (c >>> (31 - pos)) & 1 : (d >>> (31 - (pos - 27))) & 1;
                if (bit == 1) sub |= 1L << (47 - k);
            }
            schedule[toGen][0] = (int) ((sub >>> 24) & 0xffffff);
            schedule[toGen][1] = (int) (sub & 0xffffff);
        }
        return schedule;
    }

    private static long applyPermutation(long input, int[] rule) {
        long out = 0;
        for (int i = 0; i < 64; i++) {
            int src = rule[i];
            if (((input >>> (64 - src)) & 1L) != 0) out |= 1L << (63 - i);
        }
        return out;
    }

    private static int calculateSboxIndex(int a) {
        return (a & 0x20) | ((a & 0x1f) >> 1) | ((a & 0x01) << 4);
    }

    private static int applyQqPboxPermutation(int input) {
        int output = 0;
        for (int i = 0; i < 32; i++) {
            int src = P_BOX[i];
            if ((input & (1 << (32 - src))) != 0) output |= 1 << (31 - i);
        }
        return output;
    }

    private static int fFunction(int state, int keyHigh24, int keyLow24) {
        int b0 = (state >>> 24) & 0xff, b1 = (state >>> 16) & 0xff, b2 = (state >>> 8) & 0xff, b3 = state & 0xff;
        int eHigh = EBOX_HIGH[b0] | EBOX_HIGH[256 | b1] | EBOX_HIGH[512 | b2] | EBOX_HIGH[768 | b3];
        int eLow = EBOX_LOW[b0] | EBOX_LOW[256 | b1] | EBOX_LOW[512 | b2] | EBOX_LOW[768 | b3];
        int xHigh = eHigh ^ keyHigh24, xLow = eLow ^ keyLow24;
        return SP_TABLE[(xHigh >>> 18) & 0x3f] | SP_TABLE[64 | ((xHigh >>> 12) & 0x3f)]
                | SP_TABLE[128 | ((xHigh >>> 6) & 0x3f)] | SP_TABLE[192 | (xHigh & 0x3f)]
                | SP_TABLE[256 | ((xLow >>> 18) & 0x3f)] | SP_TABLE[320 | ((xLow >>> 12) & 0x3f)]
                | SP_TABLE[384 | ((xLow >>> 6) & 0x3f)] | SP_TABLE[448 | (xLow & 0x3f)];
    }

    private static void desCrypt(byte[] input, int inOff, byte[] output, int outOff, int[][] schedule) {
        int left = 0, right = 0;
        for (int i = 0; i < 8; i++) {
            int idx = (i << 8) | (input[inOff + i] & 0xff);
            left |= IP_L[idx];
            right |= IP_R[idx];
        }
        for (int i = 0; i < 15; i++) {
            int temp = right;
            right = left ^ fFunction(right, schedule[i][0], schedule[i][1]);
            left = temp;
        }
        left = left ^ fFunction(right, schedule[15][0], schedule[15][1]);
        int outLeft = 0, outRight = 0;
        for (int i = 0; i < 4; i++) {
            int idxL = (i << 8) | ((left >>> (24 - i * 8)) & 0xff);
            outLeft |= IIP_L[idxL];
            outRight |= IIP_R[idxL];
            int idxR = ((i + 4) << 8) | ((right >>> (24 - i * 8)) & 0xff);
            outLeft |= IIP_L[idxR];
            outRight |= IIP_R[idxR];
        }
        output[outOff] = (byte) (outLeft >>> 24);
        output[outOff + 1] = (byte) (outLeft >>> 16);
        output[outOff + 2] = (byte) (outLeft >>> 8);
        output[outOff + 3] = (byte) outLeft;
        output[outOff + 4] = (byte) (outRight >>> 24);
        output[outOff + 5] = (byte) (outRight >>> 16);
        output[outOff + 6] = (byte) (outRight >>> 8);
        output[outOff + 7] = (byte) outRight;
    }

    // ------------------------------------------------------------------
    // Decrypt
    // ------------------------------------------------------------------

    /**
     * Decrypts the hex payload into QRC text (decrypt → zlib inflate → strip UTF-8 BOM); the cipher is not standard DES.
     *
     * @param hex the {@code CDATA} payload of {@code lyric_download.fcg} (hex characters, may be padded)
     * @throws Exception when the hex is malformed, not a multiple of the 8-byte block or not a valid zlib stream
     */
    public static String decrypt(String hex) throws Exception {
        var enc = HexFormat.of().parseHex(hex);
        if (enc.length == 0 || enc.length % 8 != 0)
            throw new IllegalArgumentException("QRC payload is not a multiple of 8 bytes: " + enc.length);

        // Decrypt chain: D(K3) -> E(K2) -> D(K1)
        int[][] d3 = keySchedule(KEYS[2], true);
        int[][] e2 = keySchedule(KEYS[1], false);
        int[][] d1 = keySchedule(KEYS[0], true);

        byte[] t1 = new byte[8], t2 = new byte[8];
        byte[] plain = new byte[enc.length];
        for (int i = 0; i < enc.length; i += 8) {
            desCrypt(enc, i, t1, 0, d3);
            desCrypt(t1, 0, t2, 0, e2);
            desCrypt(t2, 0, plain, i, d1);
        }

        var inflater = new Inflater();
        try {
            inflater.setInput(plain);
            var out = new ByteArrayOutputStream();
            var buf = new byte[1 << 16];
            while (!inflater.finished()) {
                int n = inflater.inflate(buf);
                if (n == 0) break;
                out.write(buf, 0, n);
            }
            byte[] res = out.toByteArray();
            int off = res.length >= 3 && (res[0] & 0xff) == 0xef && (res[1] & 0xff) == 0xbb && (res[2] & 0xff) == 0xbf ? 3 : 0;
            return new String(res, off, res.length - off, StandardCharsets.UTF_8);
        } finally {
            inflater.end();
        }
    }

    /** {@code <content ...><![CDATA[<hex>]]></content>} → the hex payload; null when the body carries none. */
    private static final Pattern CDATA_HEX = Pattern.compile("<!\\[CDATA\\[\\s*([0-9a-fA-F]+)\\s*]]>");

    /**
     * Pulls the encrypted hex payload out of a {@code lyric_download.fcg} response body; null (never a throw) when
     * there is none, and the caller then falls back to the LRC route.
     */
    @Nullable
    public static String extractCipherHex(@Nullable String responseBody) {
        if (responseBody == null || responseBody.isEmpty())
            return null;
        var matcher = CDATA_HEX.matcher(responseBody);
        while (matcher.find()) {
            var hex = matcher.group(1).trim();
            // An empty lyric block is a short/blank CDATA; demanding one full 8-byte block keeps "no lyrics for this track" from looking like a decryption problem.
            if (hex.length() >= 16 && hex.length() % 16 == 0)
                return hex;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Parse
    // ------------------------------------------------------------------

    /** QRC lyric line: {@code [startMs,durationMs]word(startMs,durationMs)word(startMs,durationMs)…}. */
    private static final Pattern QRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    /** One sub-word tag; the text of a word is what stands **in front of** its tag. */
    private static final Pattern QRC_WORD = Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
    /** Any sub-word tag, stripped when the whole line text is taken. */
    private static final Pattern QRC_TAG = Pattern.compile("\\(\\d+,\\d+\\)");
    /**
     * The {@code LyricContent} attribute of the XML shell (newlines escaped as {@code \n}), located by hand and not with
     * a regex: unescaped quotes inside the value made one stop early and truncate the whole lyric — it ends at a quote
     * followed by {@code /} or {@code >}.
     */
    private static final String QRC_CONTENT_ATTR = "LyricContent=\"";

    /**
     * Parses QRC text (decrypted payload incl. XML shell) into {@link Lyric}: lines, translation pairs, per-word data.
     *
     * @param qrcXml            decrypted QRC payload ({@link #decrypt(String)} output); plain QRC text without the XML shell works too
     * @param fallbackDurationMs song duration in ms, {@code 0} when unknown; used only to close a trailing entry whose own duration is 0, since a zero-length word would make its karaoke highlight invisible
     * @return the parsed lyric, {@link Lyric#EMPTY} when there is no lyric line at all
     */
    public static Lyric parse(@Nullable String qrcXml, long fallbackDurationMs) {
        var body = qrcBody(qrcXml);
        if (body == null)
            return Lyric.EMPTY;

        // [offset:N] (milliseconds) shifts **every** timestamp below, LRC convention `t -= offset/1000`
        // (`Lyric#applyLrcOffset`; with 0 it returns the value bit for bit, and negatives are clamped to 0).
        int offsetMillis = Lyric.getLrcOffsetMillis(body, 0);

        var entries = new ArrayList<Entry>();
        for (var rawLine : body.split("\n")) {
            var line = rawLine.strip();
            if (line.isEmpty())
                continue;

            var matcher = QRC_LINE.matcher(line);
            // Attribute lines ([ti:]/[ar:]/[al:]/[by:]/[offset:]/[length:]) and the XML shell carry no
            // `start,duration` header, so they never reach the lyric map.
            if (!matcher.matches())
                continue;

            long startMs = parseLong(matcher.group(1));
            long durationMs = parseLong(matcher.group(2));
            var content = matcher.group(3);

            var words = parseWords(content, startMs, durationMs, fallbackDurationMs, offsetMillis);
            // The line text is the content with the sub-word tags stripped (the same shape the krc route builds),
            // so it reads exactly like the drawn line; a header with no text at all (an empty section marker)
            // carries nothing to draw and is skipped, exactly like the LRC route skips blank lines.
            var text = QRC_TAG.matcher(content).replaceAll("").strip();
            if (text.isEmpty())
                continue;

            entries.add(new Entry(Lyric.applyLrcOffset(startMs / 1000f, offsetMillis), text, words));
        }

        // [Credit-line filter] Same predicate as the LRC route, but it must run before the pairing: QQ's QRC payloads carry a whole
        // credit block with per-word tags at the head, and filtering after the pairing makes a credit line swallow the real lyric line next to it.
        if (Lyric.isCreditLineFilterEnabled())
            entries.removeIf(e -> Lyric.isCreditLine(e.text()));

        var lyrics = new Float2ObjectLinkedOpenHashMap<String>();
        var trans = new Float2ObjectLinkedOpenHashMap<String>();
        var wordLyrics = new Float2ObjectLinkedOpenHashMap<List<Lyric.Word>>();

        for (int i = 0; i < entries.size(); i++) {
            var cur = entries.get(i);
            var next = i + 1 < entries.size() ? entries.get(i + 1) : null;
            // QQ writes the translation as the next line, in the other script, with its own timestamp, so adjacent lines whose scripts differ are original + translation.
            // The translation is stored under the original's timestamp so both render together; info/credit lines never pair (see isInfoLine).
            if (next != null && !isInfoLine(cur.text()) && !isInfoLine(next.text())
                    && cjkOnly(cur.text()) != cjkOnly(next.text())) {
                lyrics.putIfAbsent(cur.time(), cur.text());
                trans.putIfAbsent(cur.time(), next.text());
                if (cur.words() != null)
                    wordLyrics.put(cur.time(), cur.words());
                i++;                                 // the next line was consumed as this line's translation
                continue;
            }
            lyrics.putIfAbsent(cur.time(), cur.text());
            if (cur.words() != null)
                wordLyrics.put(cur.time(), cur.words());
        }

        if (lyrics.isEmpty())
            return Lyric.EMPTY;
        return new Lyric(lyrics, trans.isEmpty() ? null : trans, wordLyrics.isEmpty() ? null : wordLyrics);
    }

    /** One parsed lyric line, before the original/translation pairing. */
    private record Entry(float time, String text, @Nullable List<Lyric.Word> words) {
    }

    /**
     * The QRC body of a decrypted payload: the {@code LyricContent} attribute for the XML shell, else the text itself
     * (the endpoint has returned both shapes).
     */
    @Nullable
    private static String qrcBody(@Nullable String qrcXml) {
        if (qrcXml == null || qrcXml.isEmpty())
            return null;

        int at = qrcXml.indexOf(QRC_CONTENT_ATTR);
        while (at >= 0) {
            int from = at + QRC_CONTENT_ATTR.length();

            // The attribute value ends at the first quote the tag continues after (`"/>` / `" >`): a quote inside
            // a lyric line is always followed by more text, an escaped `\"` is skipped as well.
            int end = -1;
            for (int i = from; i < qrcXml.length(); i++) {
                if (qrcXml.charAt(i) != '"' || (i > from && qrcXml.charAt(i - 1) == '\\'))
                    continue;
                int j = i + 1;
                while (j < qrcXml.length() && Character.isWhitespace(qrcXml.charAt(j)))
                    j++;
                if (j >= qrcXml.length() || qrcXml.charAt(j) == '/' || qrcXml.charAt(j) == '>') {
                    end = i;
                    break;
                }
            }
            if (end < 0)
                end = qrcXml.length();

            // Only the attribute value is unescaped: a plain QRC text keeps its bytes untouched.
            var content = unescape(qrcXml.substring(from, end));
            if (!content.isBlank())
                return content;
            // An empty LyricContent slot (the shell may carry several) -> try the next one.
            at = qrcXml.indexOf(QRC_CONTENT_ATTR, end + 1);
        }
        return qrcXml;
    }

    /** XML attribute escapes used inside {@code LyricContent} ({@code \n} is the normal line separator). */
    private static String unescape(String s) {
        var sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                default -> sb.append('\\').append(n);    // unknown escape: keep it verbatim
            }
        }
        return sb.toString();
    }

    /**
     * Per-word data of one line, or null when it has no sub-word tag — the renderer then draws the line as a whole,
     * like a krc line without per-character tags.
     */
    @Nullable
    private static List<Lyric.Word> parseWords(String content, long lineStartMs, long lineDurationMs,
                                              long fallbackDurationMs, int offsetMillis) {
        var matcher = QRC_WORD.matcher(content);
        var texts = new ArrayList<String>();
        var starts = new ArrayList<Long>();
        var durations = new ArrayList<Long>();
        while (matcher.find()) {
            var text = matcher.group(1);
            if (text.isEmpty())
                continue;                            // a tag without any text in front of it draws nothing
            texts.add(text);
            starts.add(parseLong(matcher.group(2)));
            durations.add(parseLong(matcher.group(3)));
        }
        if (texts.isEmpty())
            return null;

        // Absolute or relative sub-times? QQ emits **absolute** ones (the first word repeats the line start:
        // `[64920,20340]I've (64920,360)been …`), older writers wrote them relative to the line header.
        // Decided once per line from the first word, never per word.
        boolean relative = starts.get(0) < lineStartMs;
        long lineEndMs = lineStartMs + Math.max(0L, lineDurationMs);

        var out = new ArrayList<Lyric.Word>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            long startMs = relative ? lineStartMs + starts.get(i) : starts.get(i);
            long duration = durations.get(i);
            if (duration <= 0) {
                // A QRC entry normally always carries a duration; a 0 would make the karaoke highlight of that
                // word invisible. Close it with the next word's start, then the end of the line, and only for a
                // trailing entry with the song duration the caller passed (0 = unknown ⇒ nothing is invented).
                long nextStart = i + 1 < starts.size()
                        ? (relative ? lineStartMs + starts.get(i + 1) : starts.get(i + 1)) : Long.MIN_VALUE;
                long limit = nextStart > startMs ? nextStart : (lineEndMs > startMs ? lineEndMs : fallbackDurationMs);
                duration = limit > startMs ? limit - startMs : 0L;
            }
            out.add(new Lyric.Word(texts.get(i), Lyric.applyLrcOffset(startMs / 1000f, offsetMillis),
                    duration / 1000f));
        }
        return out;
    }

    /**
     * Whether a line is {@code role：value}-shaped, i.e. must not take part in translation pairing: QQ's credit block
     * carries roles {@code Lyric#isCreditLine} does not know, and such a line would pair with the next CJK line and lose
     * it. Requiring 2..12 CJK characters is what tells {@code 额外编程：…} apart from a sung duet line ({@code 男：…}).
     */
    private static boolean isInfoLine(String text) {
        var t = text.replace('：', ':').trim();
        int colon = t.indexOf(':');
        if (colon <= 0 || colon + 1 >= t.length())
            return false;
        var role = t.substring(0, colon).trim();
        if (role.length() < 2 || role.length() > 12)
            return false;
        for (int i = 0; i < role.length(); i++)
            if (role.charAt(i) < 0x2E80)                     // Latin letter/digit/symbol -> not a CJK credit role
                return false;
        return true;
    }

    /**
     * Whether a lyric line is CJK-ideograph only (no Latin letter, kana or hangul) — the original/translation criterion:
     * QQ writes the translation as the neighbouring line in the other script, and one Han character is required.
     */
    private static boolean cjkOnly(String s) {
        boolean han = false;
        for (int i = 0; i < s.length(); ) {
            int c = s.codePointAt(i);
            i += Character.charCount(c);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')) return false;
            if (c >= 0x3040 && c <= 0x30FF) return false;   // kana
            if (c >= 0x1100 && c <= 0x11FF) return false;   // hangul jamo
            if (c >= 0xAC00 && c <= 0xD7AF) return false;   // hangul syllables
            if ((c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)) han = true;
        }
        return han;
    }

    /** Digits → long; 0 when unparsable (the patterns only ever hand over digits, so this is a safety net). */
    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
