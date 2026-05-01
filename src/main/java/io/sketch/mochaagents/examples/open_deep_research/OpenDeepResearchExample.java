package io.sketch.mochaagents.examples.open_deep_research;

/**
 * {@code examples/open_deep_research/} upstream is an extended demo stack (tools, MCP, etc.).
 * MochaAgents does not replicate that subdirectory yet; extend {@code examples} primitives
 * ({@link io.sketch.mochaagents.agents.CodeAgent}, MCP client, executor) toward the same outcome.
 */
public final class OpenDeepResearchExample {

    public static void main(String[] args) {
        System.err.println("""
[MochaAgents] open_deep_research is not ported as a turnkey Java module. Compose CodeAgent \
+ tools similarly to the Python package in smolagents examples.
""");
        System.exit(args.length > 0 && "--fail".equals(args[0]) ? 1 : 0);
    }
}
