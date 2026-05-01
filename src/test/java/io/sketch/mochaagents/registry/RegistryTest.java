package io.sketch.mochaagents.registry;

import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.tools.Tool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RegistryTest {

    @Test
    void builtinCatalogCreatesKnownTool() {
        Tool t = BuiltinToolCatalog.create("web_search");
        assertFalse(t.getName().isBlank());
    }

    @Test
    void modelRegistryFallsBackToInferenceStack() {
        Model model =
            assertDoesNotThrow(
                () ->
                    ModelRegistry.create(
                        "UnknownKindForTest",
                        new ModelConnectionConfig("dummy-model", null, "", null)));
        assertNotNull(model);
    }
}
