package manager;

import java.util.Map;
import java.util.regex.Pattern;

public class LanguageSyntax {
    public final Pattern pattern;
    public final Map<String, String> styleMap;

    public LanguageSyntax(Pattern pattern, Map<String, String> styleMap) {
        this.pattern = pattern;
        this.styleMap = styleMap;
    }
}

