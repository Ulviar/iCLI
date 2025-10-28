module com.github.ulviar.icli {
    requires org.jetbrains.annotations;
    requires com.github.spotbugs.annotations;
    requires pty4j;

    exports com.github.ulviar.icli.core;
    exports com.github.ulviar.icli.core.runtime;
    exports com.github.ulviar.icli.core.runtime.diagnostics;
    exports com.github.ulviar.icli.client;
}
