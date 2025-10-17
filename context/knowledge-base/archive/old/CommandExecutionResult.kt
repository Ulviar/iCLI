package sian.grafit.command.executor

/**
 * Результат выполнения команды.
 *
 * @property exitCode Код завершения команды. Может быть `null`, если команда не была выполнена.
 * @property output Стандартный вывод команды. Может быть `null`, если команда не была выполнена.
 * @property errorMessage сообщение об ошибке, если команда завершилась с ошибкой
 */
data class CommandExecutionResult(
    val exitCode: Int?,
    val output: String?,
    val errorMessage: String
) {
    /**
     * Проверяет, была ли команда выполнена успешно.
     *
     * @return `true`, если команда завершилась с кодом 0, без сообщения об ошибке и с непустым результатом; `false` в
     * противном случае.
     */
    fun isSuccess(): Boolean = exitCode == 0 && errorMessage.isBlank() && output != null

    /**
     * Проверяет, была ли команда завершена с ошибкой. Под ошибкой понимается как ошибка выполнения команды, так и
     * ошибка обработки команды Java-машиной.
     *
     * @return `true`, если команда завершилась с ненулевым кодом или не была выполнена; `false` в противном случае.
     */
    fun isError(): Boolean = exitCode != null && exitCode != 0 || exitCode == null && errorMessage.isNotBlank()
}