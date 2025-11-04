package com.github.ulviar.icli.samples

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SamplesModuleSmokeTest {
    @Test
    fun `module metadata exposes canonical name`() {
        assertEquals("iCLI Samples", SamplesModuleMetadata.MODULE_NAME)
    }

    @Test
    fun `module metadata stays aligned with api anchor`() {
        assertSame(SamplesModuleMetadata.API_REFERENCE, SamplesReadme.apiAnchor())
    }
}
