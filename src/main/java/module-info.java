module com.github.ulviar.icli {
    requires org.jetbrains.annotations;
    requires com.github.spotbugs.annotations;
    requires pty4j;

    exports com.github.ulviar.icli.engine;
    exports com.github.ulviar.icli.engine.runtime;
    exports com.github.ulviar.icli.engine.diagnostics;
    exports com.github.ulviar.icli.engine.pool.api;
    exports com.github.ulviar.icli.engine.pool.api.hooks;
    exports com.github.ulviar.icli.client;
    exports com.github.ulviar.icli.client.pooled;
}
