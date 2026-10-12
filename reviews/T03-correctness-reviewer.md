# T03 — Correctness Review

**Verdict:** pass
**Timestamp:** 2026-10-12T21:05:30Z

## Acceptance criteria check

- [x] `src/main/resources/logback.xml` is the SLF4J configuration: console appender (`CONSOLE`), INFO default for app packages (explicit `com.fuelingchat` logger + root, both INFO). Auto-pickup proven by `LogbackConfigurationTest` (inspecting the live `LoggerContext`; no programmatic configuration anywhere in main code).
- [x] Mask rules cover `DEEPSEEK_API_KEY`, `DB_PASSWORD`, `Authorization` (incl. `Bearer`), and JSON fields `api_key` / `password` / `secret` / `token`; `MaskingTest` logs a fake secret of each kind through SLF4J and asserts the masked form appears while the plaintext never appears in the appender output (5 tests, 0 failures).
- [x] Masking is a logback filter component (`MaskingTurboFilter`) intercepting every logging call before any appender — see Finding 1 for the component-choice rationale; the test attaches a `ListAppender` to root and proves the masked copy is what reaches appenders (the original event is denied).
- [x] Main code logs via kotlin-logging only: `grep -rn "println" src/main` returns nothing; the facade is exposed through `infra.logging.LoggerProvider`.
- [x] `./gradlew build ktlintCheck detekt` — 0 errors; 21 tests total, 0 failures.

## Findings

1. **Component type deviation from the AC wording (justified):** the AC says "a logback filter applied to every appender". Implemented as a `TurboFilter` (a logback filter that runs before appender dispatch) instead of a per-appender `<filter>`. Reasons, verified against the pinned logback 1.6.5: (a) `LoggingEvent.setMessage` throws `IllegalStateException` once a message is set — a per-appender `Filter<ILoggingEvent>` cannot rewrite the event it is filtering; (b) the only filter-based rewrite (deny original + re-log masked copy) duplicates output once per appender when more than one appender exists, while a `TurboFilter` re-logs exactly once globally. The guarantee the AC exists for — secrets never reach log output, masking before write, covering every appender — holds strictly: the original event is DENIED and only the masked copy flows to appenders. `LogbackConfigurationTest.maskingFilterCoversEveryAppender` asserts the filter is active.
2. **Fixed-point masking** (informational): each mask rule replaces secret text with `***`, and re-applying any rule to `***` yields the same string, so the re-logged masked copy passes the filter with `NEUTRAL` — no recursion; verified by `innocentMessagesPassThroughUnchanged` and the per-kind tests.

## Verdict

`T03 done | verified: ok`
