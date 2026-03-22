# MAUZ Codex NetBeans Plugin

Standalone Apache NetBeans Ant module project that adds an `Ask MAUZ Codex` action to the editor popup menu and shows the captured `codex patch` output in a tool window.

## Prerequisites

- Apache NetBeans with the Plugin Development support installed
- A configured NetBeans Platform in the IDE (`Tools > NetBeans Platforms`)
- `codex` CLI available on `PATH` for the sandbox IDE process
- JDK 17

## Open And Build

1. In NetBeans, choose `File > Open Project` and open [`mauz-codex-plugin`](/C:/Users/mauz/Repositories/netbeans/mauz-codex-plugin).
2. If prompted, assign a NetBeans Platform. The project ships as a standalone module with `nbproject/platform.properties`.
3. Right-click the project and choose `Build`.

## Run In Sandbox

1. Right-click the project and choose `Run`.
2. NetBeans launches a sandbox userdir with the plugin installed.
3. Open any source file in the sandbox IDE.
4. Select some code in the editor.
5. Right-click and choose `Ask MAUZ Codex`.
6. Inspect the `MAUZ Codex Output` window for the CLI patch output or diagnostics.

## Notes

- The action is registered in the editor popup using `@ActionID`, `@ActionRegistration`, and `@ActionReference(path = "Editors/Popup")`.
- The tool window is a `TopComponent` registered in the `output` mode.
- The CLI command is built in [`CodexCliService.java`](/C:/Users/mauz/Repositories/netbeans/mauz-codex-plugin/src/org/mauz/netbeans/codex/CodexCliService.java). If your local `codex patch` syntax differs, adjust that command list there.
