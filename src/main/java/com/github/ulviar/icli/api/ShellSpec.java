package com.github.ulviar.icli.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shell invocation descriptor; empty command means direct execution. */
public record ShellSpec(List<String> command, InvocationStyle style) {
    private static final ShellSpec NONE = new ShellSpec(List.of(), InvocationStyle.DEFAULT);

    public ShellSpec {
        style = Objects.requireNonNull(style, "style");
        Objects.requireNonNull(command, "command");
        if (command.isEmpty()) {
            command = List.of();
        } else {
            List<String> immutable = List.copyOf(command);
            immutable.forEach(arg -> Objects.requireNonNull(arg, "shell command must not contain null entries"));
            command = immutable;
        }
    }

    public static ShellSpec none() {
        return NONE;
    }

    public static Builder builder() {
        return new Builder();
    }

    public enum InvocationStyle {
        DEFAULT,
        LOGIN,
        INTERACTIVE
    }

    public static final class Builder {
        private List<String> command = List.of();
        private InvocationStyle style = InvocationStyle.DEFAULT;

        private Builder() {}

        public Builder command(List<String> value) {
            Objects.requireNonNull(value, "command");
            this.command = new ArrayList<>(value);
            return this;
        }

        public Builder command(String... argv) {
            Objects.requireNonNull(argv, "argv");
            this.command = List.of(argv.clone());
            return this;
        }

        public Builder style(InvocationStyle value) {
            this.style = Objects.requireNonNull(value, "style");
            return this;
        }

        public ShellSpec build() {
            return new ShellSpec(command, style);
        }
    }
}
