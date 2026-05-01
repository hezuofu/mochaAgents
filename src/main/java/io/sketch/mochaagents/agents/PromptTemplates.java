package io.sketch.mochaagents.agents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public record PromptTemplates(
    String systemPrompt,
    PlanningPromptTemplate planning,
    ManagedAgentPromptTemplate managedAgent,
    FinalAnswerPromptTemplate finalAnswer
) {
    
    public static PromptTemplates defaultCodeAgent() {
        return loadFromYaml("code_agent");
    }
    
    public static PromptTemplates defaultStructuredCodeAgent() {
        return loadFromYaml("structured_code_agent");
    }
    
    public static PromptTemplates defaultToolCallingAgent() {
        return loadFromYaml("toolcalling_agent");
    }
    
    private static PromptTemplates loadFromYaml(String templateName) {
        // 尝试从 resources 目录加载
        Path yamlPath = Paths.get("java", "src", "main", "resources", "prompts", templateName + ".yaml");
        
        if (Files.exists(yamlPath)) {
            try {
                String yamlContent = Files.readString(yamlPath);
                return parseYaml(yamlContent);
            } catch (IOException e) {
                // 如果读取失败，返回默认值
                return getDefaultByType(templateName);
            }
        }
        
        // 尝试从类路径资源加载
        var classLoader = PromptTemplates.class.getClassLoader();
        var resource = classLoader.getResourceAsStream("prompts/" + templateName + ".yaml");
        if (resource != null) {
            try {
                String yamlContent = new String(resource.readAllBytes());
                return parseYaml(yamlContent);
            } catch (IOException e) {
                return getDefaultByType(templateName);
            }
        }
        
        // 如果文件不存在，返回默认值
        return getDefaultByType(templateName);
    }
    
    private static PromptTemplates parseYaml(String yamlContent) {
        // 简化的YAML解析实现
        // 在实际应用中，可以使用 Jackson YAML 库
        
        String systemPrompt = extractSection(yamlContent, "system_prompt:", "planning:");
        String initialPlan = extractSection(yamlContent, "initial_plan : |-", "update_plan_pre_messages:");
        String updatePlanPre = extractSection(yamlContent, "update_plan_pre_messages: |-", "update_plan_post_messages:");
        String updatePlanPost = extractSection(yamlContent, "update_plan_post_messages: |-", "managed_agent:");
        String managedAgentTask = extractSection(yamlContent, "task: |-", "report:");
        String managedAgentReport = extractSection(yamlContent, "report: |-", "final_answer:");
        String finalAnswerPre = extractSection(yamlContent, "pre_messages: |-", "post_messages:");
        String finalAnswerPost = extractSection(yamlContent, "post_messages: |-", "$");
        
        return new PromptTemplates(
            systemPrompt.trim(),
            new PlanningPromptTemplate(initialPlan.trim(), updatePlanPre.trim(), updatePlanPost.trim()),
            new ManagedAgentPromptTemplate(managedAgentTask.trim(), managedAgentReport.trim()),
            new FinalAnswerPromptTemplate(finalAnswerPre.trim(), finalAnswerPost.trim())
        );
    }
    
    private static String extractSection(String content, String startMarker, String endMarker) {
        int startIdx = content.indexOf(startMarker);
        if (startIdx == -1) return "";
        
        startIdx += startMarker.length();
        int endIdx = content.indexOf(endMarker, startIdx);
        if (endIdx == -1) endIdx = content.length();
        
        String section = content.substring(startIdx, endIdx);
        
        // 去除开头的换行和缩进
        while (section.startsWith("\n") || section.startsWith("  ")) {
            section = section.startsWith("\n") ? section.substring(1) : section.substring(2);
        }
        
        // 去除末尾的换行
        while (section.endsWith("\n")) {
            section = section.substring(0, section.length() - 1);
        }
        
        return section;
    }
    
    private static PromptTemplates getDefaultByType(String templateName) {
        switch (templateName) {
            case "structured_code_agent":
                return new PromptTemplates(
                    """
                    You are an expert assistant who can solve any task using code blobs. You will be given a task to solve as best you can.
                    To do so, you have been given access to a list of tools: these tools are basically Python functions which you can call with code.
                    To solve the task, you must plan forward to proceed in a series of steps, in a cycle of 'Thought:', 'Code:', and 'Observation:' sequences.

                    At each step, in the 'Thought:' attribute, you should first explain your reasoning towards solving the task and the tools that you want to use.
                    Then in the 'Code' attribute, you should write the code in simple Python.
                    During each intermediate step, you can use 'print()' to save whatever important information you will then need.
                    These print outputs will then appear in the 'Observation:' field, which will be available as input for the next step.
                    In the end you have to return a final answer using the `final_answer` tool. You will be generating a JSON object with the following structure:
                    {
                      "thought": "...",
                      "code": "..."
                    }
                    """,
                    new PlanningPromptTemplate(
                        """
                        You are a world expert at analyzing a situation to derive facts, and plan accordingly towards solving a task.
                        Below I will present you a task. You will need to 1. build a survey of facts known or needed to solve the task, then 2. make a plan of action to solve the task.

                        ## 1. Facts survey
                        You will build a comprehensive preparatory survey of which facts we have at our disposal and which ones we still need.

                        ### 1.1. Facts given in the task
                        ### 1.2. Facts to look up
                        ### 1.3. Facts to derive

                        ## 2. Plan
                        Then for the given task, develop a step-by-step high-level plan.
                        After writing the final step of the plan, write the '<end_plan>' tag and stop there.
                        """,
                        "You have been given the following task:\n```\n{{task}}\n```\n\nBelow you will find a history of attempts made to solve this task.",
                        """
                        Now write your updated facts below, taking into account the above history:
                        ## 1. Updated facts survey
                        ### 1.1. Facts given in the task
                        ### 1.2. Facts that we have learned
                        ### 1.3. Facts still to look up
                        ### 1.4. Facts still to derive

                        Then write a step-by-step high-level plan to solve the task above.
                        ## 2. Plan
                        After writing the final step of the plan, write the '<end_plan>' tag and stop there.
                        """
                    ),
                    new ManagedAgentPromptTemplate(
                        """
                        You're a helpful agent named '{{name}}'.
                        You have been submitted this task by your manager.
                        ---
                        Task:
                        {{task}}
                        ---
                        Your final_answer WILL HAVE to contain these parts:
                        ### 1. Task outcome (short version):
                        ### 2. Task outcome (extremely detailed version):
                        ### 3. Additional context (if relevant):
                        """,
                        "Here is the final answer from your managed agent '{{name}}':\n{{final_answer}}"
                    ),
                    new FinalAnswerPromptTemplate(
                        "An agent tried to answer a user query but it got stuck and failed to do so. You are tasked with providing an answer instead. Here is the agent's memory:",
                        "Based on the above, please provide an answer to the following user task:\n{{task}}"
                    )
                );
            case "toolcalling_agent":
                return new PromptTemplates(
                    """
                    You are an expert assistant who can solve any task using tool calls. You will be given a task to solve as best you can.
                    To do so, you have been given access to some tools.

                    The tool call you write is an action: after the tool is executed, you will get the result of the tool call as an "observation".
                    This Action/Observation can repeat N times, you should take several steps when needed.

                    To provide the final answer to the task, use an action blob with "name": "final_answer" tool.
                    Action:
                    {
                      "name": "final_answer",
                      "arguments": {"answer": "insert your final answer here"}
                    }
                    """,
                    new PlanningPromptTemplate(
                        """
                        You are a world expert at analyzing a situation to derive facts, and plan accordingly towards solving a task.
                        ## 1. Facts survey
                        ### 1.1. Facts given in the task
                        ### 1.2. Facts to look up
                        ### 1.3. Facts to derive

                        ## 2. Plan
                        Develop a step-by-step high-level plan.
                        After writing the final step of the plan, write the '<end_plan>' tag and stop there.
                        """,
                        "You have been given the following task:\n```\n{{task}}\n```\n\nBelow you will find a history of attempts made to solve this task.",
                        """
                        Now write your updated facts below:
                        ## 1. Updated facts survey
                        ### 1.1. Facts given in the task
                        ### 1.2. Facts that we have learned
                        ### 1.3. Facts still to look up
                        ### 1.4. Facts still to derive

                        ## 2. Plan
                        Write a step-by-step high-level plan.
                        After writing the final step of the plan, write the '<end_plan>' tag and stop there.
                        """
                    ),
                    new ManagedAgentPromptTemplate(
                        """
                        You're a helpful agent named '{{name}}'.
                        You have been submitted this task by your manager.
                        ---
                        Task:
                        {{task}}
                        ---
                        Your final_answer WILL HAVE to contain these parts:
                        ### 1. Task outcome (short version):
                        ### 2. Task outcome (extremely detailed version):
                        ### 3. Additional context (if relevant):
                        """,
                        "Here is the final answer from your managed agent '{{name}}':\n{{final_answer}}"
                    ),
                    new FinalAnswerPromptTemplate(
                        "An agent tried to answer a user query but it got stuck and failed to do so. You are tasked with providing an answer instead. Here is the agent's memory:",
                        "Based on the above, please provide an answer to the following user task:\n{{task}}"
                    )
                );
            case "code_agent":
            default:
                return new PromptTemplates(
                    """
                    You are an expert assistant who can solve any task using code blobs. You will be given a task to solve as best you can.
                    To do so, you have been given access to a list of tools: these tools are basically Python functions which you can call with code.
                    To solve the task, you must plan forward to proceed in a series of steps, in a cycle of Thought, Code, and Observation sequences.

                    At each step, in the 'Thought:' sequence, you should first explain your reasoning towards solving the task and the tools that you want to use.
                    Then in the Code sequence you should write the code in simple Python. The code sequence must be opened with '{{code_block_opening_tag}}', and closed with '{{code_block_closing_tag}}'.
                    During each intermediate step, you can use 'print()' to save whatever important information you will then need.
                    These print outputs will then appear in the 'Observation:' field, which will be available as input for the next step.
                    In the end you have to return a final answer using the `final_answer` tool.
                    """,
                    new PlanningPromptTemplate(
                        """
                        You are a world expert at analyzing a situation to derive facts, and plan accordingly towards solving a task.
                        Below I will present you a task. You will need to 1. build a survey of facts known or needed to solve the task, then 2. make a plan of action to solve the task.

                        ## 1. Facts survey
                        ### 1.1. Facts given in the task
                        ### 1.2. Facts to look up
                        ### 1.3. Facts to derive

                        ## 2. Plan
                        Then for the given task, develop a step-by-step high-level plan.
                        After writing the final step of the plan, write the '<end_plan>' tag and stop there.
                        """,
                        "You have been given the following task:\n```\n{{task}}\n```\n\nBelow you will find a history of attempts made to solve this task.",
                        """
                        Now write your updated facts below, taking into account the above history:
                        ## 1. Updated facts survey
                        ### 1.1. Facts given in the task
                        ### 1.2. Facts that we have learned
                        ### 1.3. Facts still to look up
                        ### 1.4. Facts still to derive

                        Then write a step-by-step high-level plan to solve the task above.
                        ## 2. Plan
                        After writing the final step of the plan, write the '<end_plan>' tag and stop there.
                        """
                    ),
                    new ManagedAgentPromptTemplate(
                        """
                        You're a helpful agent named '{{name}}'.
                        You have been submitted this task by your manager.
                        ---
                        Task:
                        {{task}}
                        ---
                        Your final_answer WILL HAVE to contain these parts:
                        ### 1. Task outcome (short version):
                        ### 2. Task outcome (extremely detailed version):
                        ### 3. Additional context (if relevant):
                        """,
                        "Here is the final answer from your managed agent '{{name}}':\n{{final_answer}}"
                    ),
                    new FinalAnswerPromptTemplate(
                        "An agent tried to answer a user query but it got stuck and failed to do so. You are tasked with providing an answer instead. Here is the agent's memory:",
                        "Based on the above, please provide an answer to the following user task:\n{{task}}"
                    )
                );
        }
    }
    
    public String populateSystemPrompt(Map<String, String> variables) {
        String result = systemPrompt;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
