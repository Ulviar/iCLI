package com.github.ulviar.icli.client;

import com.github.ulviar.icli.core.CommandDefinition;
import com.github.ulviar.icli.core.ExecutionOptions;
import com.github.ulviar.icli.core.TerminalPreference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Fluent helper for composing {@link CommandCall} instances that honour a shared command definition and execution
 * defaults.
 *
 * <p>The builder starts from the immutable {@link CommandDefinition} and {@link ExecutionOptions} supplied via
 * {@link #from(CommandDefinition, ExecutionOptions, ResponseDecoder)}. Callers then append arguments, override
 * environment variables, tweak execution options, and optionally swap the {@link ResponseDecoder}. Each mutator method
 * returns the builder itself so invocations can be chained. The builder is <strong>not</strong> thread-safe and should
 * be discarded once {@link #build()} has been called to avoid unintentionally reusing accumulated state.
 *
 * <p>The resulting {@link CommandCall} is immutable: it captures the merged command line, environment overrides,
 * working directory, terminal preference, resolved execution options, and decoder. The builder never mutates the base
 * command or options; it always derives a new instance so the defaults can be reused for future calls.
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
    private @Nullable TerminalPreference terminalPreferenceOverride;

    private CommandCallBuilder(
            CommandDefinition baseCommand, ExecutionOptions defaults, ResponseDecoder defaultDecoder) {
        this.baseCommand = Objects.requireNonNull(baseCommand, "baseCommand");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.defaultDecoder = Objects.requireNonNull(defaultDecoder, "defaultDecoder");
    }

    /**
     * Creates a new builder anchored to the supplied defaults.
     *
     * @param baseCommand    immutable command definition that provides the initial argv, environment, and working
     *                       directory. The builder copies it defensively and never mutates the original instance.
     * @param defaults       immutable execution options applied to the resulting {@link CommandCall} unless overridden
     *                       via {@link #customizeOptions(java.util.function.Consumer)}.
     * @param defaultDecoder decoder used when {@link #decoder(ResponseDecoder)} is not invoked.
     *
     * @return fresh builder ready for further customisation
     *
     * @throws NullPointerException if any argument is {@code null}
     */
    public static CommandCallBuilder from(
            CommandDefinition baseCommand, ExecutionOptions defaults, ResponseDecoder defaultDecoder) {
        return new CommandCallBuilder(baseCommand, defaults, defaultDecoder);
    }

    /**
     * Appends a single argument to the command line.
     *
     * @param value argument value; must not be {@code null}
     *
     * @return this builder
     */
    public CommandCallBuilder arg(String value) {
        extraArgs.add(Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Appends multiple arguments in the provided order.
     *
     * @param values arguments to append; {@code null} elements are rejected
     *
     * @return this builder
     */
    public CommandCallBuilder args(String... values) {
        for (String value : values) {
            arg(value);
        }
        return this;
    }

    /**
     * Appends all arguments from the iterable.
     *
     * @param values iterable of arguments; {@code null} elements are rejected
     *
     * @return this builder
     */
    public CommandCallBuilder args(Iterable<String> values) {
        for (String value : values) {
            arg(value);
        }
        return this;
    }

    /**
     * Convenience alias for {@link #arg(String)} commonly used when forming subcommands.
     *
     * @param value subcommand token
     *
     * @return this builder
     */
    public CommandCallBuilder subcommand(String value) {
        return arg(value);
    }

    /**
     * Adds a flag-style option without a value (for example {@code --verbose}).
     *
     * @param flag option token
     *
     * @return this builder
     */
    public CommandCallBuilder option(String flag) {
        return arg(flag);
    }

    /**
     * Adds a key-value option by appending both tokens to the command line.
     *
     * @param key   option key (for example {@code --file})
     * @param value option value
     *
     * @return this builder
     */
    public CommandCallBuilder option(String key, String value) {
        return arg(key).arg(value);
    }

    /**
     * Overrides or adds an environment variable for the derived command definition.
     *
     * @param key   environment key
     * @param value environment value
     *
     * @return this builder
     */
    public CommandCallBuilder env(String key, String value) {
        extraEnv.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Sets the working directory for the derived command definition.
     *
     * @param path absolute or relative path resolved by the runtime
     *
     * @return this builder
     */
    public CommandCallBuilder workingDirectory(Path path) {
        this.workingDirectory = Objects.requireNonNull(path, "path");
        return this;
    }

    /**
     * Overrides the {@link ResponseDecoder} used when interpreting command output.
     *
     * @param decoder decoder to use for the resulting {@link CommandCall}
     *
     * @return this builder
     */
    public CommandCallBuilder decoder(ResponseDecoder decoder) {
        this.decoderOverride = Objects.requireNonNull(decoder, "decoder");
        return this;
    }

    /**
     * Produces a mutable snapshot of the default {@link ExecutionOptions}, applies the provided customisation, and
     * stores the result for the upcoming build.
     *
     * <p>If invoked multiple times the latest invocation replaces the previous one.</p>
     *
     * @param customizer consumer that mutates the derived options builder
     *
     * @return this builder
     */
    public CommandCallBuilder customizeOptions(Consumer<ExecutionOptions.Builder> customizer) {
        Objects.requireNonNull(customizer, "customizer");
        ExecutionOptions.Builder builder = defaults.derive();
        customizer.accept(builder);
        this.optionsCustomizer = builder;
        return this;
    }

    /**
     * Overrides the terminal preference for the derived command definition.
     *
     * @param preference terminal preference to apply
     *
     * @return this builder
     */
    public CommandCallBuilder terminalPreference(TerminalPreference preference) {
        this.terminalPreferenceOverride = Objects.requireNonNull(preference, "preference");
        return this;
    }

    /**
     * Creates an immutable {@link CommandCall} with all accumulated modifications applied.
     *
     * @return resolved command call
     */
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
        if (terminalPreferenceOverride != null) {
            builder.terminalPreference(terminalPreferenceOverride);
        }
        return builder.build();
    }
}
