# Coder Integrator — condensed workflow (third branch)

1. Read scaffold OWNERSHIP map + cross-block interfaces + all blocks filled by coder-implementers.
2. Wire shared files only: DI modules (ClientModule.kt, ServiceModule.kt), exports, AppConfig bindings.
3. Resolve cross-block references and compile errors.
4. Run ./gradlew build ktlintCheck detekt test.
5. Max 3 retries on integration-level failures; never rewrite block internals.
6. Commit "T<n>: integrate <title>"; append STATUS: INTEGRATED to docs/tasks/T<n>.md.
