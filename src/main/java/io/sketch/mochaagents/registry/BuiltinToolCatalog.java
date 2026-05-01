package io.sketch.mochaagents.registry;

import io.sketch.mochaagents.tools.Tool;
import io.sketch.mochaagents.tools.defaults.ApiWebSearchTool;
import io.sketch.mochaagents.tools.defaults.DuckDuckGoSearchTool;
import io.sketch.mochaagents.tools.defaults.GoogleSearchTool;
import io.sketch.mochaagents.tools.defaults.UserInputTool;
import io.sketch.mochaagents.tools.defaults.VisitWebpageTool;
import io.sketch.mochaagents.tools.defaults.WebSearchTool;
import io.sketch.mochaagents.tools.defaults.WikipediaSearchTool;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Built-in CLI tool aliases (“web_search”) → constructors. Extensible at runtime ({@link #register}).
 */
public final class BuiltinToolCatalog {

    private static final ConcurrentHashMap<String, Supplier<Tool>> SUPPLIERS = new ConcurrentHashMap<>();

    static {
        resetDefaults();
    }

    private BuiltinToolCatalog() {}

    public static synchronized void resetDefaults() {
        SUPPLIERS.clear();
        register("web_search", WebSearchTool::new);
        register("google_search", GoogleSearchTool::new);
        register("api_web_search", ApiWebSearchTool::new);
        register("wikipedia", WikipediaSearchTool::new);
        register("visit_webpage", VisitWebpageTool::new);
        register("duckduckgo_search", DuckDuckGoSearchTool::new);
        register("duckduckgo", DuckDuckGoSearchTool::new);
        register("wikipedia_search", WikipediaSearchTool::new);
        register("user_input", UserInputTool::new);
        register("google", GoogleSearchTool::new);
        register("brave_search", ApiWebSearchTool::new);
    }

    public static void register(String alias, Supplier<Tool> supplier) {
        Objects.requireNonNull(alias);
        Objects.requireNonNull(supplier);
        SUPPLIERS.put(alias.strip().toLowerCase(Locale.ROOT), supplier);
    }

    public static Tool create(String alias) {
        Supplier<Tool> supplier = SUPPLIERS.get(normalize(alias));
        if (supplier == null) {
            throw new IllegalArgumentException("Unknown built-in tool alias: " + alias);
        }
        return supplier.get();
    }

    public static List<Tool> createAll(List<String> aliases) {
        List<Tool> out = new ArrayList<>(aliases.size());
        for (String a : aliases) {
            out.add(create(a));
        }
        return out;
    }

    private static String normalize(String alias) {
        return alias.strip().toLowerCase(Locale.ROOT);
    }

    public static Set<String> registeredAliases() {
        return Set.copyOf(SUPPLIERS.keySet());
    }
}
