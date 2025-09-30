package manager;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.io.IOException;

public class FunctionParser {

    private static final Pattern FUNCTION_PATTERN = Pattern.compile(
            "(public|protected|private|static|\\s)+[\\w\\<\\>\\[\\]]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{"
    );

    /**
     * Parse all functions from a Java file.
     */
    public static List<ProjectFunction> parseFunctions(Path filePath, String projectName) throws IOException {
        List<ProjectFunction> functions = new ArrayList<>();
        String content = new String(Files.readAllBytes(filePath));

        Matcher matcher = FUNCTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String signature = matcher.group();
            String functionName = matcher.group(2);

            // Find the body starting at matcher.end() - 1 (after the opening brace)
            int start = matcher.end() - 1;
            int end = findMatchingBrace(content, start);
            if (end > start) {
                String codeBody = content.substring(start + 1, end).trim();
                if (!codeBody.isEmpty()) {
                    functions.add(new ProjectFunction(
                            projectName,
                            filePath.getFileName().toString(),
                            "java",
                            functionName,
                            codeBody,
                            filePath
                    ));
                }
            }
        }

        return functions;
    }

    /**
     * Find the matching closing brace for a function body.
     */
    private static int findMatchingBrace(String text, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1; // no match found
    }
}
