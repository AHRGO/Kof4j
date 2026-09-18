package dev.kof.script;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Globals for KofScript top-level var/val — persistent across eval/repl
 * and across files in the same KofScript invocation. Backed by a
 * ConcurrentHashMap so it works for both JVM in-memory and forked runs
 * (forked runs serialize via generated Kof code, not via this map).
 */
public final class ScriptGlobals {
    private ScriptGlobals() {}
    public static final ConcurrentHashMap<String, Object> MAP = new ConcurrentHashMap<>();
    public static void put(String k, Object v) { MAP.put(k, v); }
    public static Object get(String k) { return MAP.get(k); }
    public static void clear() { MAP.clear(); }
}
