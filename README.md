# Chisel

An IntelliJ IDEA tool window that runs the Claude Code CLI installed on your machine and puts
every file write in front of you before it lands on disk.

## What it does

The Conversations tool window on the left lists every conversation; a click opens one in a tab
in the Chisel tool window on the right. Closing a tab leaves the conversation on the list —
deleting it takes a right click on the list entry and then Delete. Both panels have a button
that starts a new conversation. Each tab holds one Claude Code session: streamed text,
collapsible thinking blocks, tool calls with their arguments and results, nested subagent
messages, and the plan list rebuilt live from `TodoWrite`.

Two modes:

| Mode | Behaviour |
| --- | --- |
| Plan | `--permission-mode plan`. Write, Edit and NotebookEdit are denied without a dialog. Reading, searching and web tools work as usual. |
| Implementation | `--permission-mode manual`. Write, Edit and NotebookEdit open a diff dialog. Bash and the rest open an allow/deny dialog. Read, Glob, Grep, WebFetch, WebSearch, TodoWrite and Task run without asking. |

Switching modes sends `set_permission_mode` on the control channel, so the process keeps running.
When the agent calls `ExitPlanMode`, the plan opens in a dialog: **Start implementing** approves
the call and flips the mode, **Keep planning** denies it and sends your notes back.

## The write dialog

The current file is on the left, the agent's proposal on the right in an editable document.
The gutter carries a comment control on every line; clicking it opens an inline text field.

| What you do | What the agent receives |
| --- | --- |
| Nothing | `{"behavior":"allow"}` — the write goes through unchanged |
| Comment on lines, or edit the proposal | `{"behavior":"deny"}` with your comments and your version of the file |
| Reject and stop | `{"behavior":"deny","interrupt":true}` — the turn ends |

Any intervention denies the write, so the file never reaches disk in a state you did not accept,
and your correction stays in the conversation history. That is what makes the next file follow
a function you renamed rather than the name the agent chose.

## Rewind

Clicking a message you sent opens a confirmation showing which files will be restored and how
many messages will be dropped. Confirming sends `rewind_files` with that message's UUID, then
restarts the process with `--resume <id> --resume-session-at <uuid>` and puts the original
prompt back in the input field.

Checkpoints cover Write, Edit and NotebookEdit. Changes made through Bash and edits applied by
subagents are not restored.

## Authentication and terms

The plugin starts the unmodified `claude` binary as a subprocess and speaks its stream-json
protocol. It never reads, stores or forwards credentials; sign-in happens through Claude Code's
own flow:

```
claude auth login
```

Anthropic's [legal and compliance page](https://code.claude.com/docs/en/legal-and-compliance)
draws the line at the binary: routing Free, Pro or Max plan credentials through third-party
software is not permitted, and neither is collecting or intermediating Claude.ai session tokens,
including through the Agent SDK. The same page states that this "does not prevent an end user
from signing in to the unmodified Claude Code binary with their own Claude subscription".

This is why the plugin drives the CLI directly rather than going through the Agent SDK or the
`claude-agent-acp` adapter, which is built on that SDK. ACP was also ruled out on capability
grounds: `session/request_permission` carries a tool call id and a list of buttons, with no
channel for the proposed file content or for free-form feedback.

Per Anthropic's trademark guidelines the plugin does not use the Claude Code name or logo in its
own name or branding.

## Requirements

- IntelliJ IDEA 2025.3 or newer, on any platform variant
- JDK 21 for building
- Claude Code CLI on `PATH`, or at `~/.local/bin/claude`, `~/.claude/local/claude` or
  `~/.bun/bin/claude`

## Building

```
./gradlew buildPlugin     # artifact in build/distributions
./gradlew runIde          # sandbox IDE with the plugin loaded
```

The build compiles against IntelliJ IDEA Community 2025.3 with `untilBuild` left open, so the
resulting plugin also loads in 2026.1 and 2026.2.

## Process invocation

```
claude --print --verbose \
  --input-format stream-json --output-format stream-json \
  --include-partial-messages --replay-user-messages --forward-subagent-text \
  --permission-prompt-tool stdio \
  --permission-mode plan|manual \
  --allowedTools "Read,Glob,Grep,WebFetch,WebSearch,TodoWrite,Task" \
  --effort low \
  --session-id <uuid>
```

with `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=true` in the environment. `--bare` is deliberately
absent: it disables OAuth credential reading and forces `ANTHROPIC_API_KEY`.

`--permission-prompt-tool stdio` routes permission questions to the host over the control channel
as `can_use_tool` requests. `--permission-prompt-tool`, `--resume-session-at` and `--rewind-files`
do not appear in `claude --help`; they are documented and present in the binary.

## State on disk

Tab metadata is stored in `.idea/workspace.xml` under `ChiselConversations`. Transcripts are
written as JSONL under the IDE system directory, one file per conversation, reached through
`ProjectUtil.getProjectDataPath`. Reopening the IDE restores the conversation list and opens
the tab that was selected last; the `claude` process for a tab starts when you send the first
message in it. Deleting a conversation removes its JSONL file.

## Scope

Not implemented yet: slash commands and skills in the input field, model selection, and effort
levels other than `low`.
