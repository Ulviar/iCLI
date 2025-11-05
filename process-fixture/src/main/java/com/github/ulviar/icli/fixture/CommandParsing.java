package com.github.ulviar.icli.fixture;

final class CommandParsing {
    private CommandParsing() {}

    static int failExitCode(String command) {
        String[] parts = command.split(" ", 2);
        return parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
    }
}
