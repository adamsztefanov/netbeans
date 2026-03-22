# MAUZ Codex NetBeans Plugin

Standalone Apache NetBeans Ant module project that adds an `Ask MAUZ Codex` action to the editor popup menu, runs `codex exec` against the selected code, shows the returned patch output in a tool window, and can apply the resulting patch back to the original file.

## Prerequisites

- Apache NetBeans with the Plugin Development support installed
- A configured NetBeans Platform in the IDE (`Tools > NetBeans Platforms`)
- `codex` CLI available on `PATH` for the sandbox IDE process
- JDK 17

## Open And Build

1. In NetBeans, choose `File > Open Project` and open [`mauz-codex-plugin`](/C:/Users/mauz/Repositories/netbeans/mauz-codex-plugin).
2. If prompted, assign a NetBeans Platform. The project ships as a standalone module with `nbproject/platform.properties`.
3. Right-click the project and choose `Build`.
4. To create an installable plugin package, run `ant nbm` or `ant package-nbm`.

## Run In Sandbox

1. Right-click the project and choose `Run`.
2. NetBeans launches a sandbox userdir with the plugin installed.
3. Open any source file in the sandbox IDE.
4. Select some code in the editor.
5. Right-click and choose `Ask MAUZ Codex`.
6. Inspect the `MAUZ Codex Output` window for the CLI patch output or diagnostics.
7. Click `Apply Patch` in the tool window to write the patched selection back to the original file.

## Notes

- The action is registered in the editor popup using `@ActionID`, `@ActionRegistration`, and `@ActionReference(path = "Editors/Popup")`.
- The tool window is a `TopComponent` registered in the `output` mode.
- The CLI command is built in [`CodexCliService.java`](/C:/Users/mauz/Repositories/netbeans/mauz-codex-plugin/src/org/mauz/netbeans/codex/CodexCliService.java). If your local `codex exec` syntax or wrapper path differs, adjust that command list there.

## Example Usage

1. Open a Java source file such as `src/main/java/com/example/HelloService.java`.
2. Select a method body, for example:

```java
public String greet(String name) {
    return "Hello " + name;
}
```

3. Right-click the selection and choose `Ask MAUZ Codex`.
4. Review the unified diff in `MAUZ Codex Output`.
5. Click `Apply Patch` to replace that selected region in `HelloService.java`.
