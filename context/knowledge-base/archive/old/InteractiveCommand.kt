package sian.grafit.command.executor

import sian.utils.os.OsType
import sian.utils.os.OsUtils

object InteractiveCommand {

    val NAME = if (OsUtils.getType() == OsType.WINDOWS) "more" else "cat"
}