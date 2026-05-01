package io.sketch.mochaagents.types;

import io.sketch.mochaagents.tools.AbstractTool;
import io.sketch.mochaagents.tools.ToolInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTypesTest {

    @Test
    void unwrapArgumentsStripsAgentTypes() {
        Map<String, Object> in = Map.of(
            "a", new AgentText("hi"),
            "b", AgentImage.ofBytes(new byte[] {1, 2, 3}, "png"));
        Map<String, Object> out = AgentTypeHandlers.unwrapArguments(in);
        assertEquals("hi", out.get("a"));
        assertArrayEquals(new byte[] {1, 2, 3}, (byte[]) out.get("b"));
    }

    @Test
    void handleOutputInfersStringToAgentText() {
        Object o = AgentTypeHandlers.handleAgentOutputTypes("x", null);
        assertInstanceOf(AgentText.class, o);
        assertEquals("x", ((AgentText) o).value());
    }

    @Test
    void handleOutputTypedImage() {
        byte[] raw = new byte[] {9, 8, 7};
        Object o = AgentTypeHandlers.handleAgentOutputTypes(raw, "image");
        assertInstanceOf(AgentImage.class, o);
        assertArrayEquals(raw, (byte[]) ((AgentImage) o).toRaw());
    }

    @Test
    void agentImageFromTensor() {
        float[][][] t = new float[][][] {
            {{0f, 0f, 1f}, {1f, 0f, 0f}},
            {{0f, 1f, 0f}, {1f, 1f, 1f}}
        };
        AgentImage img = AgentImage.fromRgbTensor(t);
        assertTrue(img.getSize() > 0);
        String pathStr = img.toSerializedForm();
        assertTrue(Files.isRegularFile(Path.of(pathStr)));
    }

    @Test
    void agentAudioSerializedPathContainsWav() throws Exception {
        float[] pcm = new float[] {0f, 0.1f, -0.1f, 0.5f};
        AgentAudio a = AgentAudio.ofPcm(pcm, 16000);
        String pathStr = a.toSerializedForm();
        assertTrue(Path.of(pathStr).getFileName().toString().endsWith(".wav"));
        assertTrue(Files.exists(Path.of(pathStr)));
    }

    static final class EchoImageTool extends AbstractTool {
        EchoImageTool() {
            super("echo_img", "", Map.of("x", ToolInput.any("")), "image");
        }

        @Override
        protected Object forward(Map<String, Object> arguments) {
            return arguments.get("x");
        }

        @Override
        public boolean sanitizeAgentTypes() {
            return true;
        }
    }

    @Test
    void abstractToolSanitizeWrapsDeclaredOutputType() {
        byte[] pix = new byte[] {10, 20};
        EchoImageTool t = new EchoImageTool();
        Object out = t.call(Map.of("x", pix));
        assertInstanceOf(AgentImage.class, out);
        assertArrayEquals(pix, (byte[]) ((AgentImage) out).toRaw());
    }
}
