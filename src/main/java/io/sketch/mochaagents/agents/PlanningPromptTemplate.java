package io.sketch.mochaagents.agents;

public record PlanningPromptTemplate(
    String initialPlan,
    String updatePlanPreMessages,
    String updatePlanPostMessages
) {}