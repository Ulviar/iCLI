package com.github.ulviar.icli.engine;

import java.util.List;

/** Shell invocation descriptor; empty command means direct execution. */
public record ShellConfiguration(List<String> command, InvocationStyle style) {
    private static final ShellConfiguration NONE = new ShellConfiguration(List.of(), InvocationStyle.DEFAULT);

    public ShellConfiguration {
        command = List.copyOf(command);
    }

    public static ShellConfiguration none() {
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
            this.command = List.copyOf(value);
            return this;
        }

        public Builder command(String... argv) {
            this.command = List.of(argv);
            return this;
        }

        public Builder style(InvocationStyle value) {
            this.style = value;
            return this;
        }

        public ShellConfiguration build() {
            return new ShellConfiguration(command, style);
        }
    }
}
