package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Resolves the declared `extends` parent for class lowering (issue #302).
 *
 * `class Dog extends Animal` where Animal is an INTERFACE emitted Animal as the
 * JVM super_class_index, so the JVM rejected the class at LOAD time
 * (IncompatibleClassChangeError: class Dog has interface Animal as super class).
 * When the declared super resolves to an interface it is relocated to the
 * interfaces list and java/lang/Object becomes the real super — the same
 * contract the interpreter already gives (extends-interface behaves like
 * implements-interface). Extracted from CompilerClassLowering by the ≤500 CI
 * gate (17/09); behavior-preserving.
 */
final class ClassSuperResolution {

    private ClassSuperResolution() {
    }

    /** Effective super internal name + the final interfaces list (relocated first). */
    record Resolved(String superName, List<String> interfaces) {
    }

    static Resolved resolve(CompilerDriver driver, String superName, List<String> baseIfaces) {
        if (driver.semanticAnalyzer == null || "java/lang/Object".equals(superName)) {
            return new Resolved(superName, baseIfaces);
        }
        String simpleName = superName.contains("/")
                ? superName.substring(superName.lastIndexOf('/') + 1) : superName;
        if (!driver.semanticAnalyzer.isInterfaceType(simpleName)) {
            return new Resolved(superName, baseIfaces);
        }
        List<String> ifaces = new ArrayList<>(Stream.concat(
                Stream.of(superName), baseIfaces.stream()).toList());
        return new Resolved("java/lang/Object", ifaces);
    }
}
