package javax.script;

/**
 * Minimal stand-in for the JDK class of the same name.
 *
 * jParser's wasm loader throws {@code javax.script.ScriptException} when a module fails to
 * load, but TeaVM's browser class library has no {@code javax.script} package at all, so the
 * reference alone fails the build. Declaring it here satisfies the dependency; nothing in the
 * game catches it, and it is only ever constructed on the loader's own failure path.
 *
 * The compile task limits itself to java.base so javac accepts a package that the JDK also
 * ships (see the web module's build script).
 */
public class ScriptException extends Exception {

    public ScriptException(String message) {
        super(message);
    }

    public ScriptException(Exception cause) {
        super(cause);
    }
}
