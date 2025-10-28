package com.github.ulviar.icli.core.runtime.launch;

import com.pty4j.PtyProcess;
import java.io.IOException;

/** Factory abstraction so PTY launches can be faked in tests. */
interface PtyProcessFactory {
    PtyProcess start(PtyLaunchRequest request) throws IOException;
}
