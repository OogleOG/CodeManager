package manager;

import java.nio.file.Path;

public class ProjectFunction {
    public final String projectName;
    public final String fileName;
    public final String language;
    public final String functionName;
    public final String code;
    public final Path path;

    public ProjectFunction(String projectName, String fileName, String language, String functionName, String code, Path path) {
        this.projectName = projectName;
        this.fileName = fileName;
        this.language = language;
        this.functionName = functionName;
        this.code = code;
        this.path = path;
    }

    @Override
    public String toString() {
        return functionName + " (" + fileName + ")";
    }
}

