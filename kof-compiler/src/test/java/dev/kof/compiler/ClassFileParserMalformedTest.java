package dev.kof.compiler;

import dev.kof.compiler.parser.ClassFileParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §CodeQL uncaught-number-format-exception #247/#248 — o constPool aceita
 * UTF8 arbitrario (tag 1) em QUALQUER slot; um .class corrompido/adversario
 * lido do classpath pode colocar "#abc" onde this_class/super/NameAndType
 * esperam "#N". resolveClass e o resolvedor CONCAT davam Integer.parseInt
 * guardado SO por startsWith("#") — NFE crua no meio do parse/decompile.
 * Fix: parseCpIndex() devolve -1 em entrada nao-decimal/estourada e o
 * chamador segue o caminho no-match (usa a entrada crua). A classe REAL
 * (JDK) nunca dispara — so entrada externa, dai o teste artesanal.
 */
class ClassFileParserMalformedTest {

    private static byte[] utf8(DataOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeByte(1);
        out.writeShort(b.length);
        out.write(b);
        return b;
    }

    /** Classe minima: cp = {UTF8("#abc"), Class->2, UTF8("Foo"), Integer#99999999999999(estouro)} */
    private static byte[] corruptedThisClassPointsToHashUtf8() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);          // minor
        out.writeShort(52);         // major
        out.writeShort(5);          // constant_pool_count (slots 1..4)
        utf8(out, "#abc");       // slot1: this_class aponta aqui -> "#abc"
        out.writeByte(7);           // slot2: Class -> 3
        out.writeShort(3);
        utf8(out, "Foo");        // slot3
        out.writeByte(3);           // slot4: Integer
        out.writeInt(42);
        out.writeShort(0x21);       // accessFlags
        out.writeShort(1);          // this_class -> slot1 UTF8 "#abc"
        out.writeShort(0);          // super_class
        out.writeShort(0);          // interfaces_count
        out.writeShort(0);          // fields_count
        out.writeShort(0);          // methods_count
        out.writeShort(0);          // attributes_count
        out.flush();
        return baos.toByteArray();
    }

    private static byte[] digitOnlyButIntOverflowUtf8() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);
        out.writeShort(52);
        out.writeShort(4);          // slots 1..3
        utf8(out, "#99999999999999"); // decimal puro, estoura int (parseInt -> NFE)
        utf8(out, "Bar");
        out.writeByte(3);
        out.writeInt(7);
        out.writeShort(0x21);
        out.writeShort(1);          // this_class -> "#99999999999999"
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(0);
        out.writeShort(0);
        out.flush();
        return baos.toByteArray();
    }

    @Test
    void parseDoesNotCrashOnHashUtf8AsClassRef() throws IOException {
        byte[] cf = corruptedThisClassPointsToHashUtf8();
        ClassFileParser.ClassFile ir = assertDoesNotThrow(
                () -> ClassFileParser.parse(new ByteArrayInputStream(cf)),
                ".class com '#abc' no slot this_class deve parsear sem NFE (usa a entrada crua)");
        assertEquals("#abc", ir.thisClass,
                "resolveClass deve devolver a entrada crua quando o indice nao e' resolvivel");
    }

    @Test
    void parseDoesNotCrashOnIntOverflowDigitRef() throws IOException {
        byte[] cf = digitOnlyButIntOverflowUtf8();
        ClassFileParser.ClassFile ir = assertDoesNotThrow(
                () -> ClassFileParser.parse(new ByteArrayInputStream(cf)),
                "ref '#<estouro-int>' deve sobreviver ao parse (no-match -> entrada crua)");
        assertEquals("#99999999999999", ir.thisClass);
    }
}
