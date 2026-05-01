package io.sketch.mochaagents.types;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Audio payload aligned with smolagents {@code AgentAudio}: raw samples or file bytes plus sample rate,
 * with stringification to a path (temporary WAV when needed).
 *
 * <p>Pure-Java WAV encoding is used instead of Python {@code soundfile}/{@code torch}.</p>
 */
public final class AgentAudio implements AgentType {

    public static final int DEFAULT_SAMPLE_RATE = 16_000;

    private final Path sourcePath;
    private byte[] wavBytes;
    private final float[] pcmMono;
    private final int sampleRate;
    private final String formatHint;
    private volatile Path serializedPath;

    private AgentAudio(
        Path sourcePath,
        byte[] wavBytes,
        float[] pcmMono,
        int sampleRate,
        String formatHint
    ) {
        this.sourcePath = sourcePath;
        this.wavBytes = wavBytes;
        this.pcmMono = pcmMono;
        this.sampleRate = sampleRate > 0 ? sampleRate : DEFAULT_SAMPLE_RATE;
        this.formatHint = formatHint != null ? formatHint : "wav";
    }

    public static AgentAudio ofPath(Path path) throws IOException {
        Objects.requireNonNull(path);
        byte[] wav = Files.readAllBytes(path);
        return new AgentAudio(path.toAbsolutePath(), wav, null, guessRateFromWavHeader(wav), formatFromPath(path));
    }

    public static AgentAudio ofPath(String path) throws IOException {
        return ofPath(Path.of(path));
    }

    /** Raw WAV file contents already encoded (lazy path style if you only hold bytes). */
    public static AgentAudio ofWavBytes(byte[] wavFileBytes, int sampleRate) {
        Objects.requireNonNull(wavFileBytes);
        int rate = sampleRate > 0 ? sampleRate : guessRateFromWavHeader(wavFileBytes);
        return new AgentAudio(null, ArraysCopy(wavFileBytes), null, rate, "wav");
    }

    /** Mono PCM float samples in [-1, 1]; matches Python tuple (samplerate, ndarray) shape. */
    public static AgentAudio ofPcm(float[] monoSamples, int sampleRate) {
        Objects.requireNonNull(monoSamples);
        int rate = sampleRate > 0 ? sampleRate : DEFAULT_SAMPLE_RATE;
        return new AgentAudio(null, null, ArraysCopyFloat(monoSamples), rate, "pcm_float");
    }

    public static AgentAudio fromBase64(String base64, String format, int sampleRate) {
        byte[] data = Base64.getDecoder().decode(base64);
        if (format != null && format.equalsIgnoreCase("wav")) {
            return ofWavBytes(data, sampleRate);
        }
        return ofWavBytes(data, sampleRate > 0 ? sampleRate : guessRateFromWavHeader(data));
    }

    /** Eager-read previous API parity. */
    public static AgentAudio fromPath(Path path) throws IOException {
        return new AgentAudio(
            path.toAbsolutePath(),
            Files.readAllBytes(path),
            null,
            guessRateFromPathHeader(path),
            formatFromPath(path));
    }

    public static AgentAudio fromPath(String path) throws IOException {
        return fromPath(Path.of(path));
    }

    public static AgentAudio copy(AgentAudio other) {
        Objects.requireNonNull(other);
        if (other.sourcePath != null) {
            return new AgentAudio(other.sourcePath, null, null, other.sampleRate, other.formatHint);
        }
        if (other.wavBytes != null) {
            return ofWavBytes(ArraysCopy(other.wavBytes), other.sampleRate);
        }
        if (other.pcmMono != null) {
            return ofPcm(ArraysCopyFloat(other.pcmMono), other.sampleRate);
        }
        throw new IllegalArgumentException("Empty AgentAudio");
    }

    public static AgentAudio coerce(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot coerce null audio");
        }
        if (value instanceof AgentAudio aa) {
            return aa;
        }
        if (value instanceof byte[] b) {
            return ofWavBytes(b, guessRateFromWavHeader(b));
        }
        if (value instanceof Path p) {
            try {
                return ofPath(p);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        if (value instanceof String s) {
            try {
                return ofPath(s);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
        throw new IllegalArgumentException("Unsupported audio type: " + value.getClass().getName());
    }

    public int sampleRate() {
        return sampleRate;
    }

    /** Raw bytes of a WAV container, built from PCM lazily when needed. */
    public byte[] data() throws IOException {
        return lazyWavBytes();
    }

    public String format() {
        return formatHint;
    }

    @Override
    public Object toRaw() {
        if (pcmMono != null) {
            return ArraysCopyFloat(lazyPcmUnchecked());
        }
        try {
            return ArraysCopy(lazyWavBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
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
            Path dir = Files.createTempDirectory("mochaagents-audio-");
            Path out = dir.resolve(UUID.randomUUID() + ".wav");
            Files.write(out, lazyWavBytes());
            serializedPath = out;
            return out.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to materialize audio path", e);
        }
    }

    public String toBase64() {
        try {
            return Base64.getEncoder().encodeToString(lazyWavBytes());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public int getSize() {
        try {
            return lazyWavBytes().length;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private float[] lazyPcmUnchecked() {
        return pcmMono;
    }

    private synchronized byte[] lazyWavBytes() throws IOException {
        if (wavBytes != null) {
            return wavBytes;
        }
        if (sourcePath != null) {
            wavBytes = Files.readAllBytes(sourcePath);
            return wavBytes;
        }
        if (pcmMono != null) {
            wavBytes = encodeWavMono16(pcmMono, sampleRate);
            return wavBytes;
        }
        throw new IllegalStateException("AgentAudio has no payload");
    }

    private static int guessRateFromPathHeader(Path path) {
        try {
            byte[] head = Files.readAllBytes(path);
            return guessRateFromWavHeader(head);
        } catch (IOException e) {
            return DEFAULT_SAMPLE_RATE;
        }
    }

    private static int guessRateFromWavHeader(byte[] wav) {
        if (wav == null || wav.length < 28) {
            return DEFAULT_SAMPLE_RATE;
        }
        if (wav[0] == 'R' && wav[1] == 'I' && wav[2] == 'F' && wav[3] == 'F') {
            return ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(24);
        }
        return DEFAULT_SAMPLE_RATE;
    }

    private static byte[] encodeWavMono16(float[] samples, int sampleRate) throws IOException {
        int n = samples.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + n * 2);
        int subchunk2Size = n * 2;
        int chunkSize = 36 + subchunk2Size;

        ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        header.put(new byte[] {'R', 'I', 'F', 'F'});
        header.putInt(chunkSize);
        header.put(new byte[] {'W', 'A', 'V', 'E'});
        header.put(new byte[] {'f', 'm', 't', ' '});
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(sampleRate);
        int byteRate = sampleRate * 2;
        header.putInt(byteRate);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put(new byte[] {'d', 'a', 't', 'a'});
        header.putInt(subchunk2Size);
        out.write(header.array());

        ByteBuffer pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float s : samples) {
            float clipped = Math.max(-1f, Math.min(1f, s));
            short ss = (short) (clipped * Short.MAX_VALUE);
            pcm.putShort(ss);
        }
        out.write(pcm.array());
        return out.toByteArray();
    }

    private static byte[] ArraysCopy(byte[] data) {
        return java.util.Arrays.copyOf(data, data.length);
    }

    private static float[] ArraysCopyFloat(float[] data) {
        return java.util.Arrays.copyOf(data, data.length);
    }

    private static String formatFromPath(Path path) {
        String n = path.getFileName().toString().toLowerCase();
        if (n.endsWith(".wav")) {
            return "wav";
        }
        if (n.endsWith(".mp3")) {
            return "mp3";
        }
        if (n.endsWith(".ogg")) {
            return "ogg";
        }
        if (n.endsWith(".flac")) {
            return "flac";
        }
        return "unknown";
    }

    @Override
    public String toString() {
        return toSerializedForm();
    }
}
