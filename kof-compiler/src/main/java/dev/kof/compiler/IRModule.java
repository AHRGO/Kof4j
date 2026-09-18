package dev.kof.compiler;

import java.util.List;
import java.util.Map;
public record IRModule(String name, List<IRClass> classes, List<String> imports, String sourceName,
                       String sourceContent) {
    IRModule(String name, List<IRClass> classes, List<String> imports) {
        this(name, classes, imports, null, null);
    }

    IRModule(String name, List<IRClass> classes, List<String> imports, String sourceName) {
        this(name, classes, imports, sourceName, null);
    }
}
