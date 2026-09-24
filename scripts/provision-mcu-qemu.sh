#!/usr/bin/env bash
# provision-mcu-qemu.sh — instala, sem root, os emuladores de sistema do B-4
# (MCU) num prefixo local, espelhando o padrão do OVMF (~/.local/share/kof-ovmf).
#
# Provê `qemu-system-riscv32` (Cortex-M usa `qemu-system-arm`, B-4.3) a partir
# dos pacotes Debian/Ubuntu, extraídos com `dpkg-deb -x` e com as libs
# compartilhadas faltantes trazidas ao próprio prefixo. Ao final roda um
# self-test: monta um `KO-MCU OK` em RV32I (UART do `-M virt` + poweroff no
# test device) e exige a saída no serial — a mesma técnica da fatia B-4.1.
#
# Uso: bash scripts/provision-mcu-qemu.sh
set -euo pipefail

PREFIX="${KOF_MCU_HOME:-$HOME/.local/share/kof-mcu}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

QEMU_PKGS=(qemu-system-misc qemu-system-arm qemu-system-common)
LIB_PKGS=(libfdt1 libpmem1 libslirp0 libndctl6 libdaxctl1 librdmacm1t64)

echo "== prefix: $PREFIX"
mkdir -p "$PREFIX/usr/bin" "$PREFIX/usr/lib/x86_64-linux-gnu"

echo "== baixando pacotes (apt-get download, sem root)"
( cd "$WORK" && apt-get download "${QEMU_PKGS[@]}" "${LIB_PKGS[@]}" 2>&1 | tail -3 )

echo "== extraindo"
for deb in "$WORK"/*.deb; do dpkg-deb -x "$deb" "$PREFIX"; done

echo "== resolvendo libs faltantes (copia do próprio prefixo, iterativo)"
BIN="$PREFIX/usr/bin/qemu-system-riscv32"
LIBDIR="$PREFIX/usr/lib/x86_64-linux-gnu"
for _ in $(seq 1 40); do
    miss=$(LD_LIBRARY_PATH="$LIBDIR" ldd "$BIN" 2>/dev/null | awk '/not found/{print $1}' | sort -u) || true
    [ -z "$miss" ] && break
    for m in $miss; do
        src=$(find "$PREFIX" -name "$m*" -type f 2>/dev/null | head -1)
        [ -n "$src" ] && cp -a "$src"/* "$LIBDIR"/ 2>/dev/null || true
        [ -n "$src" ] && cp -a "$src" "$LIBDIR/$m" 2>/dev/null || true
    done
done

echo "== versão"
LD_LIBRARY_PATH="$LIBDIR" "$BIN" -version | head -1

echo "== self-test (RV32I UART + poweroff sob -M virt)"
S="$WORK/st"; mkdir -p "$S"
cat > "$S/hello.s" <<'ASM'
.option norvc
.section .text
.globl _start
_start:
    la sp, _stack_top
    la t0, msg
    li t1, 0x10000000
1:  lbu a0, 0(t0)
    beqz a0, 2f
    sb a0, 0(t1)
    addi t0, t0, 1
    j 1b
2:  li t1, 0x100000
    li t0, 0x5555
    sw t0, 0(t1)
3:  j 3b
.section .rodata
msg: .asciz "KO-MCU OK\n"
ASM
cat > "$S/link.ld" <<'LD'
ENTRY(_start)
SECTIONS {
  . = 0x80000000;
  .text : { *(.text*) }
  .rodata : { *(.rodata*) }
  .data : { *(.data*) }
  .bss : { *(.bss*) *(COMMON) }
  . = ALIGN(16);
  _stack_top = . + 0x4000;
}
LD
AS=riscv64-linux-gnu-as
LD_BIN=riscv64-linux-gnu-ld
if ! command -v "$AS" >/dev/null || ! command -v "$LD_BIN" >/dev/null; then
    echo "AVISO: binutils riscv64 ausente — self-test pulado (instale binutils-riscv64-linux-gnu)." >&2
    exit 0
fi
"$AS" -march=rv32i -mabi=ilp32 -o "$S/hello.o" "$S/hello.s"
"$LD_BIN" -m elf32lriscv -T "$S/link.ld" -o "$S/hello.elf" "$S/hello.o"
timeout 15 env LD_LIBRARY_PATH="$LIBDIR" "$BIN" \
    -M virt -bios none -display none -serial "file:$S/ser.log" \
    -kernel "$S/hello.elf" >/dev/null 2>&1 || true
if grep -q "KO-MCU OK" "$S/ser.log"; then
    echo "== OK: qemu-system-riscv32 bootou o hello RV32I ('KO-MCU OK')"
else
    echo "== FALHA: o self-test nao imprimiu 'KO-MCU OK'" >&2
    exit 1
fi

echo
echo "Prefix pronto: $PREFIX"
echo "Para rodar os testes B-4: export KOF_MCU_HOME=$PREFIX"
