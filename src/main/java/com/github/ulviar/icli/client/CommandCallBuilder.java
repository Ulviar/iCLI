package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Fluent helper for composing {@link CommandCall} instances.
 */
public final class CommandCallBuilder {

    private final CommandDefinition baseCommand;
    private final ExecutionOptions defaults;
    private final ResponseDecoder defaultDecoder;

    private final List<String> extraArgs = new ArrayList<>();
    private final Map<String, String> extraEnv = new LinkedHashMap<>();
    private @Nullable Path workingDirectory;
    private @Nullable ExecutionOptions.Builder optionsCustomizer;
    private @Nullable ResponseDecoder decoderOverride;

    private CommandCallBuilder(
            CommandDefinition baseCommand, ExecutionOptions defaults, ResponseDecoder defaultDecoder) {
        this.baseCommand = Objects.requireNonNull(baseCommand, "baseCommand");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.defaultDecoder = Objects.requireNonNull(defaultDecoder, "defaultDecoder");
    }

    public static CommandCallBuilder from(
            CommandDefinition baseCommand, ExecutionOptions defaults, ResponseDecoder defaultDecoder) {
        return new CommandCallBuilder(baseCommand, defaults, defaultDecoder);
    }

    public CommandCallBuilder arg(String value) {
        extraArgs.add(Objects.requireNonNull(value, "value"));
        return this;
    }

    public CommandCallBuilder args(String... values) {
        for (String value : values) {
            arg(value);
        }
        return this;
    }

    public CommandCallBuilder args(Iterable<String> values) {
        for (String value : values) {
            arg(value);
        }
        return this;
    }

    public CommandCallBuilder subcommand(String value) {
        return arg(value);
    }

    public CommandCallBuilder option(String flag) {
        return arg(flag);
    }

    public CommandCallBuilder option(String key, String value) {
        return arg(key).arg(value);
    }

    public CommandCallBuilder env(String key, String value) {
        extraEnv.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    public CommandCallBuilder workingDirectory(Path path) {
        this.workingDirectory = Objects.requireNonNull(path, "path");
        return this;
    }

    public CommandCallBuilder decoder(ResponseDecoder decoder) {
        this.decoderOverride = Objects.requireNonNull(decoder, "decoder");
        return this;
    }

    public CommandCallBuilder customizeOptions(Consumer<ExecutionOptions.Builder> customizer) {
        Objects.requireNonNull(customizer, "customizer");
        ExecutionOptions.Builder builder = defaults.derive();
        customizer.accept(builder);
        this.optionsCustomizer = builder;
        return this;
    }

    public CommandCall build() {
        CommandDefinition command = buildCommand();
        ExecutionOptions resolvedOptions = (optionsCustomizer != null) ? optionsCustomizer.build() : defaults;
        ResponseDecoder decoder = (decoderOverride != null) ? decoderOverride : defaultDecoder;
        return new CommandCall(command, resolvedOptions, decoder);
    }

    private CommandDefinition buildCommand() {
        CommandDefinition.Builder builder = baseCommand.derive();
        if (!extraArgs.isEmpty()) {
            List<String> merged = new ArrayList<>(baseCommand.command());
            merged.addAll(extraArgs);
            builder.command(merged);
        }
        if (!extraEnv.isEmpty()) {
            Map<String, String> merged = new LinkedHashMap<>(baseCommand.environment());
            merged.putAll(extraEnv);
            builder.environment(merged);
        }
        if (workingDirectory != null) {
            builder.workingDirectory(workingDirectory);
        }
        return builder.build();
    }
}
