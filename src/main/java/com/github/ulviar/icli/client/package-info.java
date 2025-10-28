@SuppressFBWarnings(
        value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
        justification = "Client results intentionally expose stored values/Throwables for consumers.")
@NotNullByDefault
package com.github.ulviar.icli.client;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jetbrains.annotations.NotNullByDefault;
