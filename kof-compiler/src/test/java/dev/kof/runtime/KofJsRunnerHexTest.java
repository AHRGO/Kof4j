package dev.kof.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * §CodeQL uncaught-number-format-exception #255 — `KofJsRunner.pbkdf2Hex`
 * derramava NFE crua no contexto do programa quando o guest JS passava um salt
 * hex malformado (o parseInt por par estava FORA do try). Helper isolado e
 * testavel (mesmo package — acesso package-private no classpath).
 */
class KofJsRunnerHexTest {

    @Test
    void decodesWellFormedHex() {
        assertArrayEquals(new byte[] { (byte) 0x00, (byte) 0xff, (byte) 0x7f },
                KofJsRunner.decodeHexStrict("00ff7f"));
        assertArrayEquals(new byte[] { (byte) 0xAB, (byte) 0xCD },
                KofJsRunner.decodeHexStrict("ABCDEF".substring(0, 4)), "case-insensitive");
        assertArrayEquals(new byte[0], KofJsRunner.decodeHexStrict(""), "vazio = array vazio");
    }

    @Test
    void rejectsMalformedHexWithNumberFormatNotRawCrash() {
        // odd length
        assertThrows(NumberFormatException.class, () -> KofJsRunner.decodeHexStrict("abc"),
                "comprimento impar deve rejeitar");
        // non-hex digit
        assertThrows(NumberFormatException.class, () -> KofJsRunner.decodeHexStrict("zz"),
                "digito nao-hex deve rejeitar");
        // embedded garbage
        assertThrows(NumberFormatException.class, () -> KofJsRunner.decodeHexStrict("0a2g"),
                "par valido + par invalido deve rejeitar");
        // null
        assertThrows(NumberFormatException.class, () -> KofJsRunner.decodeHexStrict(null),
                "null deve rejeitar, nao NPE");
    }
}
