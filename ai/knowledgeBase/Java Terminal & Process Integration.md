# Java Terminal & Process Integration — Knowledge Base (focus: interactive CLI apps)

**Purpose.** This document is a practical knowledge base for building a Java library that launches and controls command‑line applications across Linux, macOS, and Windows — including *fully interactive* programs (REPLs, TUIs, tools that require a real terminal). Visual styling (colors/ANSI) is deliberately out of scope.

---

## 1) Core concepts (what makes interactive apps special)

- **StdIO pipes vs TTY/PTY.** Many CLI programs change behavior when their stdin/stdout are attached to a **terminal** (TTY/PTY) instead of plain pipes. With a terminal, they may:
  - switch to line or raw input modes (read keystrokes without newline),
  - render full‑screen UI, handle window size changes,
  - prompt for passwords, control echo, react to Ctrl+C, etc.
- **PTY (Unix) / ConPTY (Windows).** A pseudoterminal provides the “it looks like a real terminal” environment. If your library needs to drive TUIs (`top`, `vi`), real REPLs (`python`, `node`), or tools that *require* a TTY (e.g., some `sudo`/`ssh` configurations), you must spawn the process under a PTY/ConPTY — **not** just pipes.
- **Shell vs direct exec.** Prefer executing binaries directly with an argument list. Only wrap in a shell when you *need* shell features (globbing, pipelines, redirection). Shell wrapping changes quoting rules and error semantics.
- **Buffering & deadlocks.** OS pipe buffers are finite. If you don’t timely drain `stdout/stderr`, the child process can block waiting for the parent to read. Your library must *always* drain both streams concurrently.

---

## 2) Cross‑OS map

- **Linux & macOS**
  - PTY is native and widely available. Shells are typically `bash` or `zsh`. Interactive tools probe `isatty()`.
- **Windows**
  - Classic console apps use the Windows Console API. For terminal emulation over byte streams, use **ConPTY** (available on modern Windows 10+). Without ConPTY, many TUIs won’t behave correctly under pipes.

> **Rule of thumb:** if a program is meant for humans (curses/TUI editors, full REPLs, password prompts, pagers), default to running it under PTY/ConPTY.

---

## 3) Process launch strategies (with pros/cons)

### 3.1 Transparent attach to the user’s console (manual interaction)
When you want to “hand the user a shell” from Java (no programmatic control).

```java
Process p = new ProcessBuilder("bash", "-l")     // or "pwsh", "-NoLogo", "-NoExit"
        .inheritIO()                             // child IO == current console IO
        .start();
int code = p.waitFor();
```

- **Pros:** simplest, truly interactive.
- **Cons:** your code cannot intercept/parse output; unsuitable for automation.

---

### 3.2 Managed process via pipes (no PTY)
Suitable for *non‑interactive* tools and simple REPLs that behave with pipes.

```java
Process p = new ProcessBuilder("python", "-i", "-u")  // -i interactive, -u unbuffered
        .redirectErrorStream(false)                   // or true to merge stderr into stdout
        .start();

var stdout = p.getInputStream();
var stderr = p.getErrorStream();
var stdin  = p.getOutputStream();

// Always drain outputs concurrently:
Thread.startVirtualThread(() -> stdout.transferTo(System.out));
Thread.startVirtualThread(() -> stderr.transferTo(System.err));

stdin.write("print(1+1)\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
stdin.flush();

int code = p.waitFor();
// Optionally close stdin to signal EOF:
stdin.close();
```

- **Pros:** zero dependencies, works everywhere.
- **Cons:** many interactive apps degrade or block when not attached to a terminal.

---

### 3.3 Managed process under PTY/ConPTY
Use a PTY library (e.g., **pty4j**) to make the child app believe it’s connected to a real terminal.

> Dependency (Gradle): `implementation("org.jetbrains.pty4j:pty4j:<latest>")`

```java
import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;

String[] cmd = (System.getProperty("os.name").startsWith("Windows"))
        ? new String[] { "pwsh.exe", "-NoLogo", "-NoExit" }
        : new String[] { "/bin/bash", "-l" };

var env = new java.util.HashMap<>(System.getenv());
env.putIfAbsent("TERM", "xterm-256color");  // safe default for TUIs

PtyProcess proc = new PtyProcessBuilder()
        .setCommand(cmd)
        .setEnvironment(env)
        .start();

// Bridge PTY <-> your chosen IO sinks/sources:
Thread.startVirtualThread(() -> { try { proc.getInputStream().transferTo(System.out); } catch (Exception ignored) {} });
Thread.startVirtualThread(() -> { try { proc.getErrorStream().transferTo(System.err); } catch (Exception ignored) {} });
System.in.transferTo(proc.getOutputStream());

int code = proc.waitFor();
```

- **Pros:** correct behavior for TUIs, REPLs, password prompts, Ctrl+C, window resize, etc.
- **Cons:** extra dependency; slight platform nuances; you should manage terminal size/resizes.

**Window size & resize:** if your UI has a notion of columns/rows, propagate it to the PTY.

```java
// Example with pty4j
proc.setWinSize(new com.pty4j.WinSize(/* cols */ 120, /* rows */ 30));
```

**Sending Ctrl+C/Ctrl+D to a PTY process:** write control bytes to stdin of the PTY:
- Ctrl+C = `\u0003` (ETX)
- Ctrl+D = `\u0004` (EOT, usually means EOF on Unix)

```java
proc.getOutputStream().write("\u0003".getBytes(java.nio.charset.StandardCharsets.UTF_8)); // send Ctrl+C
proc.getOutputStream().flush();
```

---

### 3.4 Scripted dialogs (“expect” pattern)
When an app asks for a prompt and you need to respond programmatically. Use a minimal expect‑like layer (e.g., **ExpectIt**) or roll your own.

```java
// ExpectIt example (pipes or PTY streams both work)
var p = new ProcessBuilder("/bin/sh").start();
var ex = new net.sf.expectit.ExpectBuilder()
        .withInputs(p.getInputStream(), p.getErrorStream())
        .withOutput(p.getOutputStream())
        .build();

ex.sendLine("echo ready");
ex.expect(net.sf.expectit.matcher.Matchers.contains("ready"));

ex.sendLine("read -p 'Name: ' name; echo You said:$name");
ex.expect(net.sf.expectit.matcher.Matchers.contains("Name: "));
ex.sendLine("Alice");
ex.expect(net.sf.expectit.matcher.Matchers.contains("You said:Alice"));
```

- **Tip:** for tools that *require* a TTY (e.g., some `sudo` policies), combine expect with a PTY session.

---

## 4) Process I/O & concurrency

Your library must guarantee:
- **Concurrent draining** of both `stdout` and `stderr` to prevent blocking.
- **Back‑pressure**: don’t accumulate unbounded logs in memory. Use ring buffers or streaming sinks.
- **Explicit charsets**: treat byte streams as bytes; only decode to text with a known `Charset`.
- **Graceful EOF**: optionally close the child’s stdin to signal “no more input”.

**Recommended default:** use **virtual threads** (Java 21+) for simple, robust pumps.

```java
record Pipes(Process process,
             Thread outPump,
             Thread errPump,
             java.io.OutputStream stdin) {}

static Pipes startPumps(Process p, java.io.OutputStream outSink, java.io.OutputStream errSink) {
    var outPump = Thread.startVirtualThread(() -> p.getInputStream().transferTo(outSink));
    var errPump = Thread.startVirtualThread(() -> p.getErrorStream().transferTo(errSink));
    return new Pipes(p, outPump, errPump, p.getOutputStream());
}
```

If you target older JDKs, use a fixed‑size executor and make sure the pumps are *daemon* threads so they don’t block JVM shutdown.

---

## 5) Lifecycle & termination semantics

- **Exit code:** always expose `exitCode` (blocking `waitFor()`) and a non‑blocking completion hook.
- **Timeouts:** allow `waitFor(Duration)`; if expired, send a soft termination then a hard kill.
  - Soft termination strategies:
    - via PTY: write Ctrl+C (`\u0003`) or send `exit\n` depending on app semantics,
    - via API: `process.destroy()` (SIGTERM on Unix, TerminateProcess on Windows).
  - Hard kill: `process.destroyForcibly()` after a grace period.
- **Half‑close stdin:** some tools proceed only after EOF on stdin; provide `closeStdin()` API.
- **Process groups:** consider OS‑specific “kill entire tree” options if you spawn sub‑processes via shell; otherwise children may outlive the parent process.

---

## 6) Encoding and newlines

- **Default charset:** since modern JDKs the default is UTF‑8, but **never rely on defaults** when decoding/encoding process I/O — pass an explicit `Charset` where your API returns strings.
- **Newlines:** normalize if you present line‑oriented APIs; preserve raw bytes for binary‑safe modes.

---

## 7) Security & argument handling

- **No string concatenation for commands.** Always pass `List<String>` arguments to avoid quoting issues & injection.
- **Make shell usage explicit.** Provide helpers like `Shell.wrap(command)`:
  - Unix: `["/bin/bash", "-lc", command]`
  - Windows (PowerShell): `["pwsh.exe", "-NoProfile", "-Command", command]`
- **Environment control:** accept `Map<String,String>` for environment overrides; never inherit *unintentionally* sensitive variables unless explicit.

---

## 8) Library API blueprint (suggested)

### 8.1 Data model

```java
record CommandSpec(List<String> argv,
                   Path workingDirectory,
                   Map<String,String> env,
                   boolean usePty,
                   PtyOptions pty) {}

record PtyOptions(int cols, int rows, String term);

enum StreamHandling { PIPE, INHERIT, DISCARD, MERGE_STDERR; }

record LaunchOptions(StreamHandling stdout,
                     StreamHandling stderr,
                     boolean redirectErrorStream,
                     Charset charset,
                     Duration startTimeout,
                     Duration shutdownGrace) {}
```

### 8.2 Results & streaming

```java
interface ExecHandle {
  OutputStream stdin();
  InputStream  stdout();     // optional: provide both raw bytes and decoded text variants
  InputStream  stderr();
  CompletableFuture<Integer> onExit();
  void closeStdin();         // half-close
  void resizePty(int cols, int rows); // no-op if not a PTY
  void sendCtrlC();          // PTY convenience (writes \u0003)
  boolean destroy();         // soft
  boolean destroyForcibly(); // hard
}
```

### 8.3 High-level helpers

- `run(CommandSpec, LaunchOptions)` → `CompletedProcess` (non‑interactive, collects bounded output).
- `startSession(CommandSpec, LaunchOptions)` → `ExecHandle` (interactive session).
- `expect(ExecHandle, List<ExpectStep>)` → runs scripted dialog against the active streams.
- Built‑in **bounded capture** (`maxBytes`/`maxLines`) to avoid OOM.
- **Window size propagation** for PTY sessions (`cols`, `rows`); expose `resizePty` in the handle.

---

## 9) Recipes

### 9.1 REPL under PTY (portable)
```java
CommandSpec spec = new CommandSpec(
  System.getProperty("os.name").startsWith("Windows")
    ? java.util.List.of("pwsh.exe", "-NoLogo", "-NoExit")
    : java.util.List.of("/bin/bash", "-l"),
  Path.of(System.getProperty("user.home")),
  java.util.Map.of("TERM", "xterm-256color"),
  true,
  new PtyOptions(120, 30, "xterm-256color")
);

var handle = Exec.startSession(spec, LaunchOptions.defaults());
// Hook UI streams or custom sinks here…
// Example: send a command and then Ctrl+D (EOF)
handle.stdin().write("echo $SHELL\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
handle.stdin().flush();
handle.closeStdin();
int code = handle.onExit().join();
```

### 9.2 Pipeline that needs a shell
```java
CommandSpec spec = CommandSpec.shell(
  System.getProperty("os.name").startsWith("Windows"),
  "ls -la | wc -l"  // your command string
);
// Run with pipes, collect output with a size cap
CompletedProcess cp = Exec.run(spec, LaunchOptions.textCapture(1024 * 64)); // 64 KiB cap
System.out.println(cp.exitCode());
System.out.println(cp.stdout());
```

### 9.3 Dialog that asks for a password (PTY + expect)
```java
var h = Exec.startSession(
  CommandSpec.of(List.of("sudo", "-k", "true"), /* usePty */ true),
  LaunchOptions.defaults()
);
Expect ex = ExpectDsl.wrap(h); // your thin DSL on top of the streams
ex.expectContains("password");
ex.sendLine(System.getenv("TEST_PASSWORD"));
ex.expectExitCode(0);
```

---

## 10) Testing strategy

- **Golden‑file transcripts:** capture raw streams from deterministic commands and compare against fixtures.
- **PTY and non‑PTY matrix:** run the same tests with/without PTY to ensure both code paths behave.
- **Timeout behavior:** tests must prove “soft then hard kill” contracts.
- **Stress:** run commands that write >10MB to stdout/stderr to validate pumps and bounded capture.
- **Cross‑platform CI:** exercise Linux, macOS, Windows (with ConPTY available).

---

## 11) Pitfalls checklist

- [ ] Forgot to drain `stderr` while reading `stdout` → child blocks.
- [ ] Treated bytes as `String` without specifying `Charset` → mojibake or hangs on multi‑byte splits.
- [ ] Used shell by default → quoting bugs, security risks.
- [ ] Ran TUI/REPL without PTY → no prompts, blocking reads, weird buffering.
- [ ] Didn’t propagate terminal size → broken layouts for TUIs.
- [ ] Forgot to close stdin when app expects EOF to proceed/terminate.
- [ ] Killed only the shell wrapper; left grandchildren running — consider “kill process tree”.

---

## 12) Minimal reference implementation sketch (for inspiration only)

```java
final class Exec {
  static CompletedProcess run(CommandSpec spec, LaunchOptions opt) {
    // start; drain with pumps; capture with caps; wait; return
  }
  static ExecHandle startSession(CommandSpec spec, LaunchOptions opt) {
    // start (PTY or plain Process), wire pumps, return handle
  }
  // … helpers for shell wrapping, PTY size, signal translation, etc.
}
```

**Implementation notes:**
- Use **virtual threads** for pumps; fall back to a small executor on older JDKs.
- Prefer **bounded** accumulators for captures; expose streaming sinks for unbounded consumers.
- Provide an SPI to plug a different PTY provider if needed in the future.

---

## 13) Appendix — practical bytes you may need

- **Ctrl+C / Ctrl+D bytes:** `\u0003` / `\u0004`
- **Common TERM values:** `"xterm-256color"` (good default), `"vt100"` (very conservative fallback).
- **EOF semantics:** closing the child stdin is the most portable way to say “I’m done sending input”.

---

*End of knowledge base.*
