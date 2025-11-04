package com.github.ulviar.icli.samples.scenarios.single

import com.github.ulviar.icli.samples.scenarios.single.commons.CommonsExecSingleRunExecutor
import com.github.ulviar.icli.samples.scenarios.single.icli.IcliEssentialSingleRunExecutor
import com.github.ulviar.icli.samples.scenarios.single.icli.IcliSingleRunExecutor
import com.github.ulviar.icli.samples.scenarios.single.jline.JLineSingleRunExecutor
import com.github.ulviar.icli.samples.scenarios.single.nuprocess.NuProcessSingleRunExecutor
import com.github.ulviar.icli.samples.scenarios.single.zte.ZtExecSingleRunExecutor

object SingleRunExecutors {
    @JvmStatic
    fun defaultExecutors(): List<SingleRunExecutor> =
        listOf(
            IcliEssentialSingleRunExecutor(),
            IcliSingleRunExecutor(),
            CommonsExecSingleRunExecutor(),
            ZtExecSingleRunExecutor(),
            NuProcessSingleRunExecutor(),
            JLineSingleRunExecutor(),
        )
}
