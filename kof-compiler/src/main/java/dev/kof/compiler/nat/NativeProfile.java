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
    FREESTANDING,
    /** B-2 (PLAN-BAREMETAL-BOOT): aplicação UEFI PE32+ — link estático sem
     * libc (herda o perfil FREESTANDING) + entry {@code _start} MS x64
     * (RCX=ImageHandle, RDX=SystemTable), saída via
     * {@code SystemTable->ConOut->OutputString} e
     * {@code BootServices->Exit}; o ld é seguido de
     * {@code objcopy --target=pei-x86-64 --subsystem=10}. */
    UEFI;

    /** Perfil da compilação CORRENTE — a costura ({@code RuntimePlat}) e o
     * link ({@code NativeAssembler}) são estáticos e sem parâmetro; o
     * {@code NativeBackend} grava aqui no início de cada compilação. */
    public static NativeProfile active = HOST;

    /** Aceita {@code host}/{@code freestanding}/{@code uefi} (case-insensitive); o resto é erro do chamador. */
    public static NativeProfile of(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase()) {
            case "", "host" -> HOST;
            case "freestanding", "bare" -> FREESTANDING;
            case "uefi", "efi" -> UEFI;
            default -> throw new IllegalArgumentException("unknown native profile: " + value);
        };
    }
}
