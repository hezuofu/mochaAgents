package io.sketch.mochaagents.cli;

import io.sketch.mochaagents.agents.CodeAgent;
import io.sketch.mochaagents.agents.ToolCallingAgent;
import com.smolagents.models.*;
import io.sketch.mochaagents.models.AzureOpenAIModel;
import io.sketch.mochaagents.models.HuggingFaceModel;
import io.sketch.mochaagents.models.Model;
import io.sketch.mochaagents.models.OpenAIModel;
import io.sketch.mochaagents.tools.Tool;
import com.smolagents.tools.defaults.*;
import io.sketch.mochaagents.tools.defaults.DuckDuckGoSearchTool;
import io.sketch.mochaagents.tools.defaults.VisitWebpageTool;
import io.sketch.mochaagents.tools.defaults.WikipediaSearchTool;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Cli {
    
    private static final String LEOPARD_PROMPT = "How many seconds would it take for a leopard at full speed to run through Pont des Arts?";
    private static final String VERSION = "1.0.0";
    
    private static final Map<String, Class<? extends Tool>> TOOL_MAPPING = new HashMap<>();
    static {
        TOOL_MAPPING.put("web_search", DuckDuckGoSearchTool.class);
        TOOL_MAPPING.put("wikipedia", WikipediaSearchTool.class);
        TOOL_MAPPING.put("visit_webpage", VisitWebpageTool.class);
    }
    
    public static void main(String[] args) {
        Args parsedArgs = parseArguments(args);
        
        if (parsedArgs.help) {
            printHelp();
            return;
        }
        
        if (parsedArgs.version) {
            printVersion();
            return;
        }
        
        if (parsedArgs.prompt == null) {
            runInteractiveMode();
        } else {
            runSmolagent(
                parsedArgs.prompt,
                parsedArgs.tools,
                parsedArgs.modelType,
                parsedArgs.modelId,
                parsedArgs.apiBase,
                parsedArgs.apiKey,
                parsedArgs.imports,
                parsedArgs.provider,
                parsedArgs.actionType,
                parsedArgs.verbose
            );
        }
    }
    
    private static Args parseArguments(String[] args) {
        Args result = new Args();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--help":
                case "-h":
                    result.help = true;
                    break;
                case "--version":
                case "-v":
                    result.version = true;
                    break;
                case "--model-type":
                case "-mt":
                    result.modelType = args[++i];
                    break;
                case "--action-type":
                case "-at":
                    result.actionType = args[++i];
                    break;
                case "--model-id":
                case "-mi":
                    result.modelId = args[++i];
                    break;
                case "--imports":
                case "-i":
                    while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        result.imports.add(args[++i]);
                    }
                    break;
                case "--tools":
                case "-t":
                    while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        result.tools.add(args[++i]);
                    }
                    break;
                case "--provider":
                case "-p":
                    result.provider = args[++i];
                    break;
                case "--api-base":
                case "-ab":
                    result.apiBase = args[++i];
                    break;
                case "--api-key":
                case "-ak":
                    result.apiKey = args[++i];
                    break;
                case "--verbose":
                case "-V":
                    result.verbose = true;
                    break;
                default:
                    if (!args[i].startsWith("--")) {
                        result.prompt = args[i];
                    }
                    break;
            }
        }
        
        return result;
    }
    
    private static void printHelp() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                     SmolaGents CLI - Help Documentation                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Usage: java -jar smolagents.jar [OPTIONS] [PROMPT]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -h, --help                Show this help message and exit");
        System.out.println("  -v, --version             Show version information");
        System.out.println("  -V, --verbose             Enable verbose output");
        System.out.println();
        System.out.println("Model Configuration:");
        System.out.println("  -mt, --model-type TYPE    Model type: OpenAIModel, AzureOpenAIModel,");
        System.out.println("                            HuggingFaceModel, InferenceClientModel");
        System.out.println("  -mi, --model-id ID        Model identifier (e.g., Qwen/Qwen2.5-Coder-32B-Instruct)");
        System.out.println("  -p, --provider PROVIDER   Provider name for Hugging Face models");
        System.out.println("  -ab, --api-base URL       API base URL");
        System.out.println("  -ak, --api-key KEY        API key");
        System.out.println();
        System.out.println("Agent Configuration:");
        System.out.println("  -at, --action-type TYPE   Action type: code, tool_calling");
        System.out.println("  -t, --tools TOOL...       Tools to enable (web_search, wikipedia, visit_webpage)");
        System.out.println("  -i, --imports IMPORT...   Additional authorized imports");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar smolagents.jar \"What is the capital of France?\"");
        System.out.println("  java -jar smolagents.jar -at tool_calling -t web_search wikipedia \"Search for AI news\"");
        System.out.println("  java -jar smolagents.jar -mt OpenAIModel -mi gpt-4o \"Write a Python function\"");
        System.out.println();
    }
    
    private static void printVersion() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         SmolaGents CLI                                  ║");
        System.out.println("║                         Version " + VERSION + "                           ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("An intelligent agent framework for Java.");
        System.out.println("https://github.com/huggingface/smolagents");
        System.out.println();
    }
    
    private static void runInteractiveMode() {
        printBanner();
        
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("\n[1/5] Select Action Type");
        System.out.println("────────────────────────");
        System.out.println("What action type would you like to use?");
        System.out.print("'code' or 'tool_calling'? [code]: ");
        String actionType = scanner.nextLine().trim();
        if (actionType.isEmpty()) actionType = "code";
        
        System.out.println("\n[2/5] Select Tools");
        System.out.println("──────────────────");
        System.out.println("Available Tools:");
        int idx = 1;
        for (Map.Entry<String, Class<? extends Tool>> entry : TOOL_MAPPING.entrySet()) {
            System.out.println("  " + idx++ + ". " + entry.getKey());
        }
        System.out.println("\nEnter tool names separated by spaces (e.g., 'web_search')");
        System.out.print("Select tools for your agent [web_search]: ");
        String toolsInput = scanner.nextLine().trim();
        List<String> tools = toolsInput.isEmpty() ? List.of("web_search") : Arrays.asList(toolsInput.split(" "));
        
        System.out.println("\n[3/5] Model Configuration");
        System.out.println("─────────────────────────");
        System.out.print("Model type [InferenceClientModel]: ");
        String modelType = scanner.nextLine().trim();
        if (modelType.isEmpty()) modelType = "InferenceClientModel";
        
        System.out.print("Model ID [Qwen/Qwen2.5-Coder-32B-Instruct]: ");
        String modelId = scanner.nextLine().trim();
        if (modelId.isEmpty()) modelId = "Qwen/Qwen2.5-Coder-32B-Instruct";
        
        System.out.println("\n[4/5] Advanced Options");
        System.out.println("──────────────────────");
        System.out.print("Configure advanced options? [no]: ");
        String advanced = scanner.nextLine().trim().toLowerCase();
        String provider = null;
        String apiBase = null;
        String apiKey = null;
        List<String> imports = new ArrayList<>();
        
        if ("yes".equals(advanced) || "y".equals(advanced)) {
            if (modelType.contains("InferenceClient") || modelType.contains("OpenAI") || modelType.contains("LiteLLM")) {
                System.out.print("Provider: ");
                provider = scanner.nextLine().trim();
                System.out.print("API Base URL: ");
                apiBase = scanner.nextLine().trim();
                System.out.print("API Key: ");
                apiKey = scanner.nextLine().trim();
            }
            
            System.out.print("Additional imports (space-separated): ");
            String importsInput = scanner.nextLine().trim();
            if (!importsInput.isEmpty()) {
                imports = Arrays.asList(importsInput.split(" "));
            }
        }
        
        System.out.println("\n[5/5] Task Description");
        System.out.println("──────────────────────");
        System.out.println("What task would you like the agent to perform?");
        System.out.print("[" + LEOPARD_PROMPT + "]: ");
        String prompt = scanner.nextLine().trim();
        if (prompt.isEmpty()) prompt = LEOPARD_PROMPT;
        
        scanner.close();
        
        printSeparator();
        System.out.println("Starting agent execution...");
        System.out.println("Time: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        printSeparator();
        
        runSmolagent(prompt, tools, modelType, modelId, apiBase, apiKey, imports, provider, actionType, true);
    }
    
    private static void printBanner() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        \uD83E\uDD16 SmolaGents CLI                          ║");
        System.out.println("║                     Intelligent agents at your service                   ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
    
    private static void printSeparator() {
        System.out.println();
        System.out.println("─".repeat(70));
        System.out.println();
    }
    
    private static Model loadModel(String modelType, String modelId,
                                   String apiBase, String apiKey, String provider) {
        loadEnv();
        
        switch (modelType) {
            case "OpenAIModel":
                return OpenAIModel.builder()
                    .apiKey(apiKey != null ? apiKey : System.getenv("FIREWORKS_API_KEY"))
                    .apiBase(apiBase != null ? apiBase : "https://api.fireworks.ai/inference/v1")
                    .modelId(modelId)
                    .build();
            case "AzureOpenAIModel":
                return AzureOpenAIModel.builder()
                    .apiKey(apiKey != null ? apiKey : System.getenv("AZURE_OPENAI_API_KEY"))
                    .apiBase(apiBase)
                    .modelId(modelId)
                    .build();
            case "HuggingFaceModel":
                return HuggingFaceModel.builder()
                    .modelId(modelId)
                    .apiKey(apiKey != null ? apiKey : System.getenv("HF_API_KEY"))
                    .provider(provider)
                    .build();
            case "InferenceClientModel":
            default:
                return HuggingFaceModel.builder()
                    .modelId(modelId)
                    .apiKey(apiKey != null ? apiKey : System.getenv("HF_API_KEY"))
                    .provider(provider)
                    .build();
        }
    }
    
    private static void runSmolagent(String prompt, List<String> tools, String modelType,
                                     String modelId, String apiBase, String apiKey,
                                     List<String> imports, String provider, String actionType,
                                     boolean verbose) {
        try {
            Model model = loadModel(modelType, modelId, apiBase, apiKey, provider);
            
            if (verbose) {
                System.out.println("Model Loaded: " + modelType + " (" + modelId + ")");
                System.out.println("Action Type: " + actionType);
                System.out.println("Tools: " + tools);
                if (!imports.isEmpty()) {
                    System.out.println("Additional Imports: " + imports);
                }
                System.out.println();
            }
            
            List<Tool> availableTools = new ArrayList<>();
            for (String toolName : tools) {
                if (toolName.contains("/")) {
                    String spaceName = toolName.substring(toolName.lastIndexOf("/") + 1)
                        .toLowerCase().replace("-", "_").replace(".", "_");
                    System.out.println("Loading tool from Hugging Face Space: " + toolName);
                } else {
                    Class<? extends Tool> toolClass = TOOL_MAPPING.get(toolName);
                    if (toolClass != null) {
                        try {
                            availableTools.add(toolClass.getDeclaredConstructor().newInstance());
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to create tool: " + toolName, e);
                        }
                    } else {
                        throw new IllegalArgumentException("Tool " + toolName + " is not recognized");
                    }
                }
            }
            
            Object result;
            if ("code".equals(actionType)) {
                CodeAgent agent = CodeAgent.builder()
                    .tools(availableTools)
                    .model(model)
                    .additionalAuthorizedImports(imports)
                    .build();
                result = agent.run(prompt);
                agent.cleanup();
            } else if ("tool_calling".equals(actionType)) {
                ToolCallingAgent agent = ToolCallingAgent.builder()
                    .tools(availableTools)
                    .model(model)
                    .build();
                result = agent.run(prompt);
            } else {
                throw new IllegalArgumentException("Unsupported action type: " + actionType);
            }
            
            if (verbose) {
                printSeparator();
                System.out.println("╔══════════════════════════════════════════════════════════════════════════╗");
                System.out.println("║                           FINAL RESULT                                  ║");
                System.out.println("╚══════════════════════════════════════════════════════════════════════════╝");
                System.out.println();
                System.out.println(result);
                System.out.println();
                printSeparator();
            }
            
        } catch (Exception e) {
            System.err.println();
            System.err.println("╔══════════════════════════════════════════════════════════════════════════╗");
            System.err.println("║                           ERROR OCCURRED                                 ║");
            System.err.println("╚══════════════════════════════════════════════════════════════════════════╝");
            System.err.println();
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            System.exit(1);
        }
    }
    
    private static void loadEnv() {
        try {
            FileInputStream fis = new FileInputStream(".env");
            Properties props = new Properties();
            props.load(fis);
            for (String key : props.stringPropertyNames()) {
                if (System.getenv(key) == null) {
                    System.setProperty(key, props.getProperty(key));
                }
            }
            fis.close();
        } catch (IOException e) {
            // Ignore if .env file not found
        }
    }
    
    private static class Args {
        String prompt;
        List<String> tools = new ArrayList<>(List.of("web_search"));
        String modelType = "InferenceClientModel";
        String modelId = "Qwen/Qwen2.5-Coder-32B-Instruct";
        String provider;
        String apiBase;
        String apiKey;
        List<String> imports = new ArrayList<>();
        String actionType = "code";
        boolean verbose = false;
        boolean help = false;
        boolean version = false;
    }
}
