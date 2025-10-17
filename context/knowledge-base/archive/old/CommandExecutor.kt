package sian.grafit.command.executor

/**
 * Интерфейс для выполнения консольных команд.
 */
interface CommandExecutor {

    /**
     * Выполняет команду с заданными аргументами и входными данными.
     *
     * @param command команда для выполнения
     * @param args список аргументов команды
     * @param input входные данные для команды
     * @return результат выполнения команды в виде [CommandExecutionResult]
     */
    fun execute(command: String, args: List<String>, input: String): CommandExecutionResult
}