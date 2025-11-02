# Public API Boundaries

This note records the package-level contracts enforced by ICLI-020 so maintainers and contributors can keep future
changes aligned with the published surface.

## Exported packages (stable)

| Package | Audience | Contents |
| --- | --- | --- |
| `com.github.ulviar.icli.client` | Essential API consumers | `CommandService`, session runners, client-facing result types |
| `com.github.ulviar.icli.engine` | Advanced API consumers | Command specification, execution options, process SPI contracts |
| `com.github.ulviar.icli.engine.runtime` | Advanced API consumers | `StandardProcessEngine`, `ProcessEngineExecutionException`, `ProcessShutdownException` |
| `com.github.ulviar.icli.engine.pool.api` | Advanced pooling consumers | `ProcessPool`, `WorkerLease`, metrics/diagnostics interfaces |
| `com.github.ulviar.icli.engine.pool.api.hooks` | Advanced pooling consumers | Reset, warmup, and timeout hook contracts |
| `com.github.ulviar.icli.engine.diagnostics` | Optional diagnostics subscribers | `DiagnosticsListener`, `DiagnosticsEvent`, `StreamType` |

Exported packages must honour semantic versioning once the library is published. Breaking changes require a major
release or an explicit migration guide captured in the roadmap.

## Internal packages (non-exported)

All implementation details live under the following namespaces and may change without notice:

- `com.github.ulviar.icli.engine.runtime.internal.*`
- `com.github.ulviar.icli.engine.pool.internal.*`

Classes in these packages should remain `public` only when cross-package access is required (e.g., from
`com.github.ulviar.icli.engine.runtime`). Avoid referencing them from public APIs, documentation, or samples.

## Documentation alignment

- The root [README.md](../../../README.md) lists the same packages in the “Supported Packages” section for consumers.
- When adding new exported packages, update both this document and the module descriptor (`module-info.java`).
- Whenever an internal helper becomes part of the public surface, move it to an exported package and extend the README
  and architecture brief accordingly.
