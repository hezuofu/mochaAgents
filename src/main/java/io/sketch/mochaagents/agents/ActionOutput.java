package io.sketch.mochaagents.agents;

public record ActionOutput(
    Object output,
    boolean isFinalAnswer
) implements StreamEvent {}
