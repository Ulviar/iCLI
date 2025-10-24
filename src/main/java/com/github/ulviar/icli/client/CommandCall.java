package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;

/** Immutable snapshot of a command invocation assembled by {@link CommandCallBuilder}. */
public record CommandCall(CommandDefinition command, ExecutionOptions options, ResponseDecoder decoder) {}
