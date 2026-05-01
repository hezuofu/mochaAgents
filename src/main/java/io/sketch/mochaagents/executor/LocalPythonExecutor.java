package io.sketch.mochaagents.executor;

import io.sketch.mochaagents.tools.Tool;
import org.python.core.PyBoolean;
import org.python.core.PyDictionary;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyNone;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.python.core.PyException;
import org.python.core.Py;
import org.python.util.PythonInterpreter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalPythonExecutor implements PythonExecutor {

    private final Map<String, Object> state = new HashMap<>();
    private final Map<String, Tool> tools = new HashMap<>();
    private final Set<String> authorizedImports;
    private final int maxPrintOutputsLength;
    private final int timeoutSeconds;
    private final StringBuilder printOutputs = new StringBuilder();
    
    private PythonInterpreter interpreter;
    
    private static final Set<String> BASE_BUILTIN_MODULES = Set.of(
        "math", "random", "json", "re", "datetime", "collections", "itertools"
    );
    
    private static final Set<String> DANGEROUS_MODULES = Set.of(
        "io", "multiprocessing", "os", "pathlib",
        "pty", "shutil", "socket", "subprocess"
    );
    
    private static final int DEFAULT_MAX_LEN_OUTPUT = 50000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    public LocalPythonExecutor(List<String> additionalAuthorizedImports) {
        this(additionalAuthorizedImports, DEFAULT_MAX_LEN_OUTPUT, null, DEFAULT_TIMEOUT_SECONDS);
    }
    
    public LocalPythonExecutor(List<String> additionalAuthorizedImports, 
                               Integer maxPrintOutputsLength,
                               Map<String, Object> additionalFunctions,
                               Integer timeoutSeconds) {
        this.maxPrintOutputsLength = maxPrintOutputsLength != null ? maxPrintOutputsLength : DEFAULT_MAX_LEN_OUTPUT;
        this.timeoutSeconds = timeoutSeconds != null ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        
        Set<String> imports = new HashSet<>(BASE_BUILTIN_MODULES);
        if (additionalAuthorizedImports != null) {
            imports.addAll(additionalAuthorizedImports);
        }
        this.authorizedImports = Set.copyOf(imports);
        
        state.put("__name__", "__main__");
        if (additionalFunctions != null) {
            state.putAll(additionalFunctions);
        }
        
        initializeInterpreter();
    }
    
    private void initializeInterpreter() {
        Properties props = new Properties();
        props.setProperty("python.home", "");
        props.setProperty("python.path", "");
        PythonInterpreter.initialize(System.getProperties(), props, new String[0]);
        
        interpreter = new PythonInterpreter();
        setupBaseEnvironment();
    }
    
    private void setupBaseEnvironment() {
        interpreter.exec(BASE_ENVIRONMENT_CODE);
        
        for (String module : authorizedImports) {
            if (!DANGEROUS_MODULES.contains(module)) {
                interpreter.exec(String.format("import %s", module));
            }
        }
    }
    
    /**
     * Jython 2.7 bootstrap for {@code FinalAnswerException} plus capture of {@code print} output.
     * Global import monkey-patching is intentionally omitted because Jython’s stdlib (json, encodings, …)
     * traverses modules such as {@code os} during interpreter boot; tightening would break all runs.
     * Illicit imports are still guarded by {@link #authorizedImports} when auto-importing whitelisted modules above.
     */
    private static final String BASE_ENVIRONMENT_CODE =
        "from __future__ import print_function\n" +
            "import __builtin__\n" +
            "\n" +
            "class FinalAnswerException(BaseException):\n" +
            "    def __init__(self, value):\n" +
            "        self.value = value\n" +
            "\n" +
            "_print_outputs = []\n" +
            "\n" +
            "def _agent_print(*args, **kwargs):\n" +
            "    _print_outputs.append(' '.join(map(str, args)))\n" +
            "\n" +
            "__builtin__.print = _agent_print\n";
    
    @Override
    public void sendTools(Map<String, Tool> tools) {
        this.tools.putAll(tools);
        
        for (Map.Entry<String, Tool> entry : tools.entrySet()) {
            String toolName = entry.getKey();
            Tool tool = entry.getValue();
            
            PyObject pyTool = new PyObject() {
                public PyObject __call__(PyObject[] args, String[] keywords) {
                    Map<String, Object> arguments = new HashMap<>();
                    
                    if (keywords != null && keywords.length > 0) {
                        for (int i = 0; i < keywords.length && i < args.length; i++) {
                            arguments.put(keywords[i], convertPyObject(args[i]));
                        }
                    } else if (args.length == 1 && args[0] instanceof PyDictionary) {
                        PyDictionary dict = (PyDictionary) args[0];
                        for (int j = 0; j < dict.__len__(); j++) {
                            PyObject key = (PyObject) dict.keys().__getitem__(new PyInteger(j));
                            arguments.put(key.toString(), convertPyObject(dict.__finditem__(key)));
                        }
                    } else {
                        List<Object> argsList = new ArrayList<>();
                        for (PyObject arg : args) {
                            argsList.add(convertPyObject(arg));
                        }
                        arguments.put("args", argsList);
                    }
                    
                    Object result = tool.call(arguments);
                    return convertToPyObject(result);
                }
            };
            
            interpreter.set(toolName, pyTool);
        }
        
        if (tools.containsKey("final_answer")) {
            interpreter.exec(
                "def final_answer(*args, **kwargs):\n" +
                "    raise FinalAnswerException(globals()['final_answer'](*args, **kwargs))\n"
            );
        }
    }
    
    @Override
    public void sendVariables(Map<String, Object> variables) {
        state.putAll(variables);
        
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            interpreter.set(entry.getKey(), convertToPyObject(entry.getValue()));
        }
    }
    
    @Override
    public CodeOutput execute(String codeAction) {
        printOutputs.setLength(0);
        
        String fixedCode = PythonFinalAnswerNormalizer.fix(codeAction);
        
        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Object> future = executor.submit(() -> executeWithTimeout(fixedCode));
            
            Object result;
            try {
                result = future.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return CodeOutput.intermediate(null, "Error: Code execution timed out after " + timeoutSeconds + " seconds");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return CodeOutput.intermediate(null, "Error: Execution interrupted");
            } catch (ExecutionException e) {
                return CodeOutput.intermediate(null, "Error: Execution error: " + e.getCause().getMessage());
            } finally {
                executor.shutdown();
            }
            
            String logs = getPrintOutputs();
            if (logs.length() > maxPrintOutputsLength) {
                logs = logs.substring(0, maxPrintOutputsLength) + "... [truncated]";
            }
            
            if (result instanceof FinalAnswerException) {
                return CodeOutput.finalAnswer(((FinalAnswerException) result).getValue().toString());
            }
            
            String resultStr = result != null ? result.toString() : null;
            return CodeOutput.intermediate(resultStr, logs);
            
        } catch (Exception e) {
            String logs = getPrintOutputs();
            if (logs.length() > maxPrintOutputsLength) {
                logs = logs.substring(0, maxPrintOutputsLength) + "... [truncated]";
            }
            return CodeOutput.intermediate(null, "Error: " + e.getMessage() + "\nLogs: " + logs);
        }
    }
    
    private Object executeWithTimeout(String code) {
        if (authorizedImports.contains("*")) {
            throw new SecurityException("Wildcard imports are not allowed for security reasons");
        }
        
        try {
            interpreter.exec(code);
            
            PyObject lastResult = interpreter.get("_");
            
            PyObject pyState = interpreter.get("_print_outputs");
            if (pyState instanceof PyList) {
                PyList outputList = (PyList) pyState;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < outputList.__len__(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(outputList.__getitem__(new PyInteger(i)).toString());
                }
                printOutputs.append(sb);
            }
            
            return convertPyObject(lastResult);
        } catch (PyException e) {
            String msg = e.toString();
            if (msg != null && msg.contains("FinalAnswerException")) {
                String answer = extractAnswerFromException(msg);
                return new FinalAnswerException(answer);
            }
            
            PyObject pyState = interpreter.get("_print_outputs");
            if (pyState instanceof PyList) {
                PyList outputList = (PyList) pyState;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < outputList.__len__(); i++) {
                    if (i > 0) sb.append("\n");
                    sb.append(outputList.__getitem__(new PyInteger(i)).toString());
                }
                printOutputs.append(sb);
            }
            
            throw new RuntimeException(msg != null ? msg : e.toString(), e);
        }
    }
    
    private String getPrintOutputs() {
        return printOutputs.toString();
    }
    
    private String extractAnswerFromException(String msg) {
        Pattern pattern = Pattern.compile("FinalAnswerException\\((.*?)\\)");
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
            String answer = matcher.group(1).trim();
            if ((answer.startsWith("'") && answer.endsWith("'")) || 
                (answer.startsWith("\"") && answer.endsWith("\""))) {
                answer = answer.substring(1, answer.length() - 1);
            }
            return answer;
        }
        return msg;
    }
    
    private Object convertPyObject(PyObject obj) {
        if (obj == null || obj == Py.None) {
            return null;
        }
        if (obj instanceof PyInteger) {
            return ((PyInteger) obj).getValue();
        }
        if (obj instanceof PyFloat) {
            return ((PyFloat) obj).getValue();
        }
        if (obj instanceof PyString) {
            return obj.toString();
        }
        if (obj instanceof PyBoolean) {
            return ((PyBoolean) obj).getValue();
        }
        if (obj instanceof PyList) {
            PyList list = (PyList) obj;
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < list.__len__(); i++) {
                result.add(convertPyObject(list.__getitem__(new PyInteger(i))));
            }
            return result;
        }
        if (obj instanceof PyDictionary) {
            PyDictionary dict = (PyDictionary) obj;
            Map<Object, Object> result = new HashMap<>();
            for (int i = 0; i < dict.__len__(); i++) {
                PyObject key = (PyObject) dict.keys().__getitem__(new PyInteger(i));
                result.put(convertPyObject(key), convertPyObject(dict.__finditem__(key)));
            }
            return result;
        }
        if (obj instanceof PyTuple) {
            PyTuple tuple = (PyTuple) obj;
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < tuple.size(); i++) {
                PyObject item = tuple.__getitem__(i);
                result.add(convertPyObject(item));
            }
            return result;
        }
        return obj.toString();
    }
    
    private PyObject convertToPyObject(Object obj) {
        if (obj == null) {
            return Py.None;
        }
        if (obj instanceof Integer) {
            return new PyInteger((Integer) obj);
        }
        if (obj instanceof Double) {
            return new PyFloat((Double) obj);
        }
        if (obj instanceof String) {
            return new PyString((String) obj);
        }
        if (obj instanceof Boolean) {
            return Py.newBoolean((Boolean) obj);
        }
        if (obj instanceof List) {
            List<?> list = (List<?>) obj;
            PyObject[] items = new PyObject[list.size()];
            for (int i = 0; i < list.size(); i++) {
                items[i] = convertToPyObject(list.get(i));
            }
            return new PyList(items);
        }
        if (obj instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) obj;
            PyDictionary dict = new PyDictionary();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                dict.__setitem__(convertToPyObject(entry.getKey()), convertToPyObject(entry.getValue()));
            }
            return dict;
        }
        if (obj instanceof Number) {
            return new PyInteger(((Number) obj).intValue());
        }
        return new PyString(obj.toString());
    }
    
    @Override
    public void cleanup() {
        state.clear();
        tools.clear();
        if (interpreter != null) {
            interpreter.cleanup();
        }
    }
    
    public static class FinalAnswerException {
        private final Object value;
        
        public FinalAnswerException(Object value) {
            this.value = value;
        }
        
        public Object getValue() {
            return value;
        }
    }
}