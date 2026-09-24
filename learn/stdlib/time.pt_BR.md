[English](time.md) | [Português](time.pt_BR.md)

# kof.time — calendário civil e relógios

> **Status: estável em JVM/Script, Native x86-64, JS; cross/corner faces rastreada no ledger de paridade** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|

| Função | Forma |
|--------|-------|
| `sleep` | `sleep(Int ms) -> void` |
| `now` | `now() -> Long` |
| `collect` | `collect() -> void` |
| `interval` | `interval(Int ms, callback) -> String` |
| `cancel` | `cancel(String id) -> void` |
| `isLeapYear` | `isLeapYear(Int year) -> Bool` |
| `daysInMonth` | `daysInMonth(Int year, Int month) -> Int` |
| `dayOfWeek` | `dayOfWeek(Int y, Int m, Int d) -> Int` |
| `isWeekend` | `isWeekend(Int y, Int m, Int d) -> Bool` |
| `daysBetween` | `daysBetween(Int y1, Int m1, Int d1, Int y2, Int m2, Int d2) -> Int` |
| `isToday` | `isToday(Int y, Int m, Int d) -> Bool` |
| `addDays` | `addDays(String iso, Int days) -> String` |
| `diffDays` | `diffDays(String isoA, String isoB) -> Int` |
| `todayIso` | `todayIso() -> String` |
| `formatDateIso` | `formatDateIso(Int y, Int m, Int d) -> String` |
| `parseDateIso` | `parseDateIso(String iso) -> Int` |
| `tzOffsetSeconds` | `tzOffsetSeconds() -> Int` |
| `hoursBetween` | `hoursBetween(Int y1, Int m1, Int d1, Int h1, Int y2, Int m2, Int d2, Int h2) -> Int` |
```
```kf
time.todayIso()                // 2026-09-24
var t = time.now()             // epoch millis
time.isLeapYear(2024)          // true
time.addDays("2026-09-24", 7)  // 2026-10-01
```
```

> Paridade: new faces: cross golden ⏳, `addDays`/`diffDays` cross `TIME002`, JS `collect` `TIME004` — ledger row 8.

**Veja também:** [39 — Standard Library universal](../39-stdlib.pt_BR.md) — a história completa e a tabela honesta de paridade.
