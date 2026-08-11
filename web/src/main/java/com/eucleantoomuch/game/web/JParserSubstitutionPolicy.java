package com.eucleantoomuch.game.web;

import java.util.function.Predicate;
import org.teavm.extension.spi.substitution.SimpleSubstitutionPolicy;
import org.teavm.extension.spi.substitution.SubstitutionSink;

/**
 * Makes TeaVM pick jParser's wasm loader instead of its JNI one.
 *
 * gdx-teavm ships a substitution policy that redirects classes to their {@code emu.*} twin,
 * but it only covers com.badlogic.gdx, net.mgsx and org.jbox2d. jParser - the layer Jolt's
 * bindings load their native module through - is not in that list, so its {@code emu} loader
 * from {@code loader-teavm} was being ignored and the JNI original (System.loadLibrary, which
 * does not exist in a browser) failed the build the moment anything touched Jolt.
 *
 * Substitution is an SPI, so the missing rule can simply be contributed here; the class is
 * registered in META-INF/services/org.teavm.extension.spi.substitution.SubstitutionPolicy.
 */
public class JParserSubstitutionPolicy extends SimpleSubstitutionPolicy {

    @Override
    public void contribute(SubstitutionSink sink) {
        // Each artifact declares its own prefix in META-INF/teavm.properties, and they differ:
        // loader-teavm ships `emu.com=com`, while idl-teavm, idl-helper-teavm and jolt-teavm
        // ship `gen.com=com` / `gen.jolt=jolt`. Mixing them up leaves calls pointing at the
        // desktop signatures (long addresses instead of int) and they resolve to nothing.
        Predicate<String> jParserLoader = inPackage("com.github.xpenatan.jParser.loader", true);
        Predicate<String> jParserRest = inPackage("com.github.xpenatan.jParser", true);
        Predicate<String> jParserIdl = inPackage("com.github.xpenatan.jparser", true);
        Predicate<String> joltClasses = inPackage("jolt", true);

        sink.selectClasses(jParserLoader).packagePrefix("emu");
        sink.selectClasses(not(jParserLoader).and(jParserRest)).packagePrefix("gen");
        sink.selectClasses(jParserIdl).packagePrefix("gen");
        sink.selectClasses(joltClasses).packagePrefix("gen");
    }
}
