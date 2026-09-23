package dev.kof.compiler.nat;

/**
 * B-1 (PLAN-BAREMETAL-BOOT): perfil de link do alvo nativo.
 *
 * <p>{@link #HOST} é o padrão: Linux + libc, ligação dinâmica
 * ({@code -dynamic-linker … -lc}) — comportamento atual. {@link #FREESTANDING}
 * liga estático e sem libc ({@code ld -o bin obj}): o programa fala com o SO
 * só pela costura {@code kof_plat_*} (B-0). Capacidades que dependem de libc
 * (DB/MySQL/concurrency/pow/FFI) são recusadas com diagnóstico — nunca caem
 * silenciosamente.
 */
public enum NativeProfile {
    HOST,
    FREESTANDING;

    /** Aceita {@code host}/{@code freestanding} (case-insensitive); o resto é erro do chamador. */
    public static NativeProfile of(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase()) {
            case "", "host" -> HOST;
            case "freestanding", "bare" -> FREESTANDING;
            default -> throw new IllegalArgumentException("unknown native profile: " + value);
        };
    }
}
