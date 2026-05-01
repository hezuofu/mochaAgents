package io.sketch.mochaagents.executor;

import java.util.regex.Pattern;

/**
 * Aligns with smolagents <code>fix_final_answer_code</code>: disambiguates
 * <code>final_answer(...)</code> calls from accidental variable assignments named <code>final_answer</code>.
 */
public final class PythonFinalAnswerNormalizer {

    private static final Pattern ASSIGNMENT_PATTERN =
        Pattern.compile("(?<!\\.)(?<!\\w)\\bfinal_answer\\s*=");
    private static final Pattern VAR_ASSIGN_REGEX =
        Pattern.compile("(?<!\\.)(?<!\\w)(\\bfinal_answer)(\\s*=)");
    private static final Pattern VAR_USAGE_REGEX =
        Pattern.compile("(?<!\\.)(?<!\\w)(\\bfinal_answer\\b)(?!\\s*\\()");

    private PythonFinalAnswerNormalizer() {}

    public static String fix(String code) {
        if (code == null || !code.contains("final_answer(") || !ASSIGNMENT_PATTERN.matcher(code).find()) {
            return code;
        }
        code = VAR_ASSIGN_REGEX.matcher(code).replaceAll("final_answer_variable$2");
        code = VAR_USAGE_REGEX.matcher(code).replaceAll("final_answer_variable");
        return code;
    }
}
