package io.sketch.mochaagents.examples.gradio_ui;

import io.sketch.mochaagents.ui.SimpleAgentHttpServer;

/**
 * Parity landing class for upstream {@code examples/gradio_ui.py}: launches the JDK-based
 * single-page UI ({@link SimpleAgentHttpServer}) or {@code Cli serve}/{@code webagent}.
 */
public final class GradioUiExample {

    public static void main(String[] args) throws Exception {
        SimpleAgentHttpServer.main(args);
    }
}
