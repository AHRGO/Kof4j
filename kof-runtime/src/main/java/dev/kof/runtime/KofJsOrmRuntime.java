package dev.kof.runtime;

import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.Value;

/**
 * Instalacao das primitivas de plataforma {@code kof.orm} no JS (ORM001, 18/09).
 *
 * <p>Extraido de {@link KofJsRunner} (regra de estabilidade: <=500 linhas) —
 * mesma familia do {@code kof.db}: cada {@code platform.put} embrulha um metodo
 * estatico de {@link KofJsOrmBridge} (SQL replicado de {@code JvmOrmRuntime},
 * sobre as MESMAS conexoes do {@link KofJsDbBridge}) num {@link ProxyExecutable}
 * que converte erros de ponte em excecao de script limpa (R6, nunca stack de
 * proxy). O registro chega serializado ({@code toJSON}) como objeto de chaves
 * limpas; o bind tipado da volta acontece no guest ({@code JsRuntimeOps}
 * {@code __kof_decode_<T>}), dando paridade byte-a-byte com o JVM.
 */
final class KofJsOrmRuntime {

    private KofJsOrmRuntime() {
    }

    static void install(java.util.Map<String, Object> platform) {
        platform.put("ormCreate", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.create(args[0].asString(), args[1].asString(), args[2].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormSave", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.save(args[0].asString(), args[1], args[2].asString(), args[3].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormSaveAll", (ProxyExecutable) args -> {
            try {
                java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
                Value arr = args[1];
                if (arr != null && arr.hasArrayElements()) {
                    for (long i = 0; i < arr.getArraySize(); i++) {
                        items.add(KofJsDbBridge.valueMap(arr.getArrayElement(i)));
                    }
                }
                return KofJsOrmBridge.saveAll(args[0].asString(), items, args[2].asString(), args[3].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormFind", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.find(args[0].asString(), KofJsDbBridge.fromGuest(args[1]),
                        args[2].asString(), args[3].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormAll", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.all(args[0].asString(), args[1].asString(), args[2].asString())
                        .toArray(new String[0]);
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormWhere", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.where(args[0].asString(), args[1].asString(),
                        KofJsDbBridge.fromGuest(args[2]), args[3].asString(), args[4].asString())
                        .toArray(new String[0]);
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormWhereOp", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.whereOp(args[0].asString(), args[1].asString(), args[2].asString(),
                        KofJsDbBridge.fromGuest(args[3]), args[4].asString(), args[5].asString())
                        .toArray(new String[0]);
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormCount", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.count(args[0].asString(), args[1].asString(), args[2].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormCountWhere", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.countWhere(args[0].asString(), args[1].asString(),
                        KofJsDbBridge.fromGuest(args[2]), args[3].asString(), args[4].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormDelete", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.delete(args[0].asString(), KofJsDbBridge.fromGuest(args[1]),
                        args[2].asString(), args[3].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormDeleteAll", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.deleteAll(args[0].asString(), args[1].asString(), args[2].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormPage", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.page(args[0].asString(), KofJsDbBridge.fromGuest(args[1]),
                        KofJsDbBridge.fromGuest(args[2]), args[3].asString(), args[4].asString())
                        .toArray(new String[0]);
            } catch (Exception e) {
                throw guestError(e);
            }
        });
        platform.put("ormMigrate", (ProxyExecutable) args -> {
            try {
                return KofJsOrmBridge.migrate(args[0].asString(), args[1].asString(), args[2].asString());
            } catch (Exception e) {
                throw guestError(e);
            }
        });
    }

    /** Erro de ponte vira excecao de script com mensagem limpa (cf.
     *  {@link KofJsRunner}): o catch do programa Kof ve a causa, nao stack de
     *  proxy. Mesma semantica do caminho {@code kof.db}. */
    private static RuntimeException guestError(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        return new RuntimeException(msg);
    }
}
