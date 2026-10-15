package pro.sketchware.resolver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.CRC32;
import java.util.zip.DeflaterOutputStream;

/** Builds the files the tests put in a project. */
final class TestFiles {
    static final String VECTOR = "<vector xmlns:android=\"http://schemas.android.com/apk/res/android\" android:width=\"24dp\" "
            + "android:height=\"24dp\" android:viewportWidth=\"24\" android:viewportHeight=\"24\">"
            + "<path android:fillColor=\"#FF000000\" android:pathData=\"M19,13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z\"/></vector>";

    private TestFiles() {
    }

    static File write(File root, String relativePath, String content) throws IOException {
        return write(root, relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    static File write(File root, String relativePath, byte[] content) throws IOException {
        File file = new File(root, relativePath);
        File parent = file.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("mkdirs " + parent);
        Files.write(file.toPath(), content);
        return file;
    }

    /** A real, decodable PNG of one colour. */
    static byte[] png(int width, int height) {
        try {
            ByteArrayOutputStream raw = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(raw)) {
                for (int y = 0; y < height; y++) {
                    deflate.write(0);
                    for (int x = 0; x < width; x++) deflate.write(new byte[]{(byte) 0xFF, 0, 0, (byte) 0xFF});
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream header = new DataOutputStream(ihdr);
            header.writeInt(width);
            header.writeInt(height);
            header.write(new byte[]{8, 6, 0, 0, 0});
            chunk(out, "IHDR", ihdr.toByteArray());
            chunk(out, "IDAT", raw.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        stream.writeInt(data.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        stream.write(typeBytes);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }

    /** The start of a lossy WebP file of this size (a header is all the resolver reads). */
    static byte[] webpLossy(int width, int height) {
        byte[] data = new byte[30];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
        data[4] = 22;
        System.arraycopy("WEBPVP8 ".getBytes(StandardCharsets.US_ASCII), 0, data, 8, 8);
        data[16] = 10;
        // frame tag at 20..22, start code at 23..25, then 14-bit width and height
        data[23] = (byte) 0x9D;
        data[24] = 0x01;
        data[25] = 0x2A;
        data[26] = (byte) (width & 0xFF);
        data[27] = (byte) ((width >> 8) & 0x3F);
        data[28] = (byte) (height & 0xFF);
        data[29] = (byte) ((height >> 8) & 0x3F);
        return data;
    }

    /** A lossless WebP header of this size. */
    static byte[] webpLossless(int width, int height) {
        byte[] data = new byte[30];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
        System.arraycopy("WEBPVP8L".getBytes(StandardCharsets.US_ASCII), 0, data, 8, 8);
        data[20] = 0x2F;
        int w = width - 1;
        int h = height - 1;
        data[21] = (byte) (w & 0xFF);
        data[22] = (byte) (((w >> 8) & 0x3F) | ((h & 0x03) << 6));
        data[23] = (byte) ((h >> 2) & 0xFF);
        data[24] = (byte) ((h >> 10) & 0x0F);
        return data;
    }

    /** An extended WebP header of this size. */
    static byte[] webpExtended(int width, int height) {
        byte[] data = new byte[30];
        System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, data, 0, 4);
        System.arraycopy("WEBPVP8X".getBytes(StandardCharsets.US_ASCII), 0, data, 8, 8);
        int w = width - 1;
        int h = height - 1;
        data[24] = (byte) (w & 0xFF);
        data[25] = (byte) ((w >> 8) & 0xFF);
        data[26] = (byte) ((w >> 16) & 0xFF);
        data[27] = (byte) (h & 0xFF);
        data[28] = (byte) ((h >> 8) & 0xFF);
        data[29] = (byte) ((h >> 16) & 0xFF);
        return data;
    }

    static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
