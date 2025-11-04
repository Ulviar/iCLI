package com.github.ulviar.icli.samples

import com.github.ulviar.icli.client.CommandService

/**
 * Simple helpers that describe the samples module until real scenarios land.
 */
object SamplesReadme {
    fun modulePurpose(): String = """Demonstrates iCLI ergonomics alongside alternative libraries for single-run scenarios."""

    fun apiAnchor(): Class<*> = CommandService::class.java
}
