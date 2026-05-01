package io.sketch.mochaagents.types;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Image payload aligned with smolagents {@code AgentImage}: behaves like raw bytes for tooling and can
 * stringify to a filesystem path for prompts/logs.
 *
 * <p>Supported sources: in-memory PNG/JPEG/etc. bytes, another {@link AgentImage}, or a lazily-read file
 * {@link Path}. Torch / NumPy tensors are not supported here; convert to RGB bytes first.</p>
 */
public final class AgentImage implements AgentType {

    private final Path sourcePath;
    private byte[] cachedBytes;
    private final String formatHint;
    private volatile Path serializedPath;

    private AgentImage(Path sourcePath, byte[] cachedBytes, String formatHint) {
        this.sourcePath = sourcePath;
        this.cachedBytes = cachedBytes;
        this.formatHint = formatHint != null ? formatHint : "unknown";
    }

    public static AgentImage ofPath(String path) throws IOException {
        return ofPath(Path.of(path));
    }

    public static AgentImage ofPath(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a regular file: " + path);
        }
        return new AgentImage(path.toAbsolutePath(), null, formatFromPath(path));
    }

    public static AgentImage ofBytes(byte[] data, String format) {
        Objects.requireNonNull(data, "data");
        return new AgentImage(null, ArraysCopy(data), format != null ? format : "png");
    }

    public static AgentImage fromBase64(String base64, String format) {
        byte[] data = Base64.getDecoder().decode(base64);
        return ofBytes(data, format != null ? format : "png");
    }

    /** Convenience: eagerly load file bytes into memory (previous record-style API). */
    public static AgentImage fromPath(String path) throws IOException {
        return fromPath(Path.of(path));
    }

    public static AgentImage fromPath(Path path) throws IOException {
        return ofBytes(Files.readAllBytes(path), formatFromPath(path));
    }

    public static AgentImage copy(AgentImage other) {
        Objects.requireNonNull(other, "other");
        if (other.sourcePath != null) {
            return new AgentImage(other.sourcePath, null, other.formatHint);
        }
        return ofBytes(ArraysCopy(other.lazyBytesUnchecked()), other.formatHint);
    }

    public static AgentImage coerce(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot coerce null image");
        }
        if (value instanceof AgentImage ai) {
            return ai;
        }
        if (value instanceof byte[] b) {
            return ofBytes(b, "png");
        }
        if (value instanceof Path p) {
            try {
                return ofPath(p);
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid image path: " + p, e);
            }
        }
        if (value instanceof String s) {
            try {
                return ofPath(s);
            } catch (IOException e) {
                throw new IllegalArgumentException("Invalid image path: " + s, e);
            }
        }
        throw new IllegalArgumentException("Unsupported image type: " + value.getClass().getName());
    }

    private static byte[] ArraysCopy(byte[] data) {
        return java.util.Arrays.copyOf(data, data.length);
    }

    private byte[] lazyBytesUnchecked() {
        if (cachedBytes != null) {
            return cachedBytes;
        }
        if (sourcePath != null) {
            try {
                cachedBytes = Files.readAllBytes(sourcePath);
                return cachedBytes;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read image path " + sourcePath, e);
            }
        }
        throw new IllegalStateException("AgentImage has no bytes or path");
    }

    private synchronized void ensureBytesLoaded() {
        lazyBytesUnchecked();
    }

    @Override
    public Object toRaw() {
        ensureBytesLoaded();
        return cachedBytes.clone();
    }

    @Override
    public String toSerializedForm() {
        if (serializedPath != null) {
            return serializedPath.toAbsolutePath().toString();
        }
        if (sourcePath != null) {
            return sourcePath.toAbsolutePath().toString();
        }
        try {
            Path dir = Files.createTempDirectory("mochaagents-img-");
            Path out = dir.resolve(UUID.randomUUID() + ".png");
            byte[] raw = lazyBytesUnchecked();
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(raw));
            if (bi == null) {
                Files.write(out, raw);
            } else {
                ImageIO.write(bi, "png", out.toFile());
            }
            serializedPath = out;
            return out.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to materialize image path", e);
        }
    }

    /** Encoded image bytes (lazy load from path). */
    public byte[] data() {
        ensureBytesLoaded();
        return cachedBytes.clone();
    }

    public String format() {
        return formatHint;
    }

    public String toBase64() {
        return Base64.getEncoder().encodeToString(lazyBytesUnchecked());
    }

    public int getSize() {
        return lazyBytesUnchecked().length;
    }

    /**
     * Normalized float tensor {@code [H][W][C]} with values in [0,1], {@code C=1} or {@code 3}.
     * Converts to an in-memory {@link AgentImage} without persisting a path.
     */
    public static AgentImage fromRgbTensor(float[][][] tensor) {
        Objects.requireNonNull(tensor, "tensor");
        if (tensor.length == 0 || tensor[0].length == 0) {
            throw new IllegalArgumentException("Empty tensor");
        }
        int h = tensor.length;
        int w = tensor[0].length;
        int c = tensor[0][0].length;
        if (c != 1 && c != 3) {
            throw new IllegalArgumentException("Channel count must be 1 or 3, got " + c);
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float[] px = tensor[y][x];
                if (c == 3) {
                    int r = clamp255(px[0] * 255f);
                    int g = clamp255(px[1] * 255f);
                    int b = clamp255(px[2] * 255f);
                    int rgb = (r << 16) | (g << 8) | b;
                    img.setRGB(x, y, rgb);
                } else {
                    int gray = clamp255(px[0] * 255f);
                    int gb = (gray << 16) | (gray << 8) | gray;
                    img.setRGB(x, y, gb);
                }
            }
        }
        try {
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return ofBytes(bos.toByteArray(), "png");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode tensor image", e);
        }
    }

    private static int clamp255(float v) {
        int i = Math.round(v);
        return Math.max(0, Math.min(255, i));
    }

    @Override
    public String toString() {
        return toSerializedForm();
    }

    private static String formatFromPath(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".png")) {
            return "png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "gif";
        }
        if (fileName.endsWith(".webp")) {
            return "webp";
        }
        return "unknown";
    }
}
