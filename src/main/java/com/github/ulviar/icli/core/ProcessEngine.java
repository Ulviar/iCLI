package com.github.ulviar.icli.core;

/**
 * Pluggable gateway encapsulating the core execution engine.
 *
 * <p>This interface will be implemented by the runtime once the process execution core exists. The API layer depends on
 * it to run single-shot commands and start interactive sessions.
 */
public interface ProcessEngine {

    ProcessResult run(CommandDefinition spec, ExecutionOptions options);

    InteractiveSession startSession(CommandDefinition spec, ExecutionOptions options);
}
