package com.github.ulviar.icli.samples;

import com.github.ulviar.icli.client.CommandService;

/**
 * Lightweight marker object that keeps the samples module connected to the core library.
 */
public final class SamplesModuleMetadata {

    public static final String MODULE_NAME = "iCLI Samples";

    /**
     * Provides a stable reference to the primary public API so IDEs surface the correct module dependency.
     */
    public static final Class<?> API_REFERENCE = CommandService.class;

    private SamplesModuleMetadata() {
        // utility class
    }
}
