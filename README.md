# Fueling Chat Backend

## Purpose
Production-grade Kotlin/Ktor backend for an AI chat over fueling data. The user asks about a fueling by ID; the backend gathers data from four Postgres databases via a Koog tool and returns an LLM summary. Read-only DB access guaranteed.

## Stack
- Kotlin, Ktor (netty, content-negotiation, kotlinx-serialization)
- Koog 1.3.0 (agent framework, tool calling)
- Kodein DI
- Exposed + HikariCP (read-only)
- JUnit 5 + MockK
- LLM providers: DeepSeek v4-pro / v4-flash, local Ollama option

## The first/second experiment
This repo hosts a controlled experiment comparing two AI development pipelines over the same backlog (T11-T15):
- first: single-agent pipeline, one strong model (deepseek-v4-pro) writes everything
- second: dual-coder pipeline, architect (deepseek-v4-pro) scaffolds, implementer (deepseek-v4-flash) fills

Both run the same 5 sequential quality gates (test, correctness, simplicity, git, code-style).

Headline results (details in comparison-report.md): second wins on cost and quality (0 handbacks vs 3, ~60x fewer cache-read tokens, ~81x cheaper overall); first wins on simple-task wall-clock; second wins on complex-task wall-clock (T12: 28m vs 43m).

## Prerequisites
- JDK 17+
- Docker (local Postgres databases)
- DeepSeek API key, or Ollama for the local provider

## Setup

    cp .env.example .env
    docker compose up -d
    ./gradlew run

## API contract

    POST /chat
    body: { "prompt": string, "provider": "local"|"remote", optional fueling pin }
    response: { "reply": string, "provider": string, masked errors per CONTRACT-API-002 }

## Layout

    src/main/kotlin: routes/, services/, infra/llm/, infra/db/, config/, di/
    reviews/: per-task gate verdicts
    metrics/: cto-ledger.md, incidents.md
    docs/tasks/: backlog

## Quality commands

    ./gradlew build ktlintCheck detekt test

## Multi-agent harness
.claude/agents/ holds 11 agent definitions with model: frontmatter; .claude/commands/process.md orchestrates real sub-agent spawning (Agent tool), token logging per spawn, sequential gates, handback protocol. Branches first/second hold the completed experiment runs.
