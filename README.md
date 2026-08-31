# Chisel

An IntelliJ IDEA tool window that runs the Claude Code CLI installed on your machine and puts
every file write in front of you before it lands on disk.

![Chisel prompt input](screenshots/prompt.png)

## Conversations

The Conversations tool window on the left lists every conversation, with a search field, and a
right click offers Rename and Delete; deleting also offers to remove the underlying Claude CLI
session file. Selecting an entry shows it in the Chisel tool window on the right, one
conversation at a time. The `+` button in the Chisel header starts a new one.

Sessions started in the terminal from the same project directory are read in the background and
join the list with their transcripts. A conversation carries streamed text, collapsible thinking
blocks, tool calls with their arguments and results, nested subagent messages, and the plan list
rebuilt live from `TodoWrite`.

The conversation toolbar holds Continue, Compact, Subagents, Remote control, Export and Delete.
Export writes the transcript as Markdown. Next to the prompt input sit the context meter, the
token counts and the session cost.

## Modes

| Mode | Behaviour |
| --- | --- |
| Ask | Every tool that asks for permission is denied with a note explaining that the conversation is read-only. Reading, searching and web tools work as usual. |
| Plan | `--permission-mode plan`. Write, Edit and NotebookEdit are denied without a dialog. |
| Implementation | `--permission-mode manual`. Write, Edit and NotebookEdit open a diff dialog. Bash and the rest open an allow/deny dialog. |
| Auto | `--permission-mode auto`. The CLI decides; the dialogs still appear for anything it escalates. |

Read, Glob, Grep, WebFetch, WebSearch, TodoWrite and Task run without asking in every mode.
Switching modes sends `set_permission_mode` on the control channel, so the process keeps running.
When the agent calls `ExitPlanMode`, the plan opens in a dialog: **Start implementing** approves
the call and flips the mode, **Keep planning** denies it and sends your notes back.

Model and effort are set per conversation from the bar under the prompt. Settings | Tools |
Chisel holds the default mode and a model and effort pair for Plan and for Implementation; Ask
and Auto start from the Plan pair. Effort ranges from Low to Max, plus Ultracode, which passes
`--settings {"ultracode":true}`.

## The write dialog

The current file is on the left, the agent's proposal on the right in an editable document.
The gutter carries a comment control on every line; clicking it opens an inline text field.

| What you do | What the agent receives |
| --- | --- |
| Nothing | `{"behavior":"allow"}` — the write goes through unchanged |
| Comment on lines, or edit the proposal | `{"behavior":"deny"}` with your comments and your version of the file |
| Reject and stop | `{"behavior":"deny","interrupt":true}` — the turn ends |

Any intervention denies the write, and the correction stays in the conversation history.

Shell commands open their own dialog with the command and the agent's description of it. In
Implementation mode the system prompt is extended with a request for commands that read clearly:
one thing per call, no unrelated work chained with `&&`, long option names, a description on
every call. Questions from `AskUserQuestion` open as a dialog with the options as buttons.

## Attachments

Files and images attach to a prompt through the paperclip button, drag and drop, or a paste from
the clipboard. Images travel as base64 content blocks; other files travel as a path the agent
reads. Screenshots pasted into the input are written to the IDE system directory, which the CLI
receives as `--add-dir`.

In IDEs with the Database plugin, a second button lists the schemas of every configured data
source. The chosen schemas are rendered as `CREATE TABLE` text — columns with types, defaults and
comments, primary and foreign keys with their referential actions, indexes, and views as their
column list — written to a `.sql` file and attached like any other file. A data source that has
not been introspected yet is introspected on selection. In IDEs without the Database plugin the
button is absent.

## Rewind

Clicking a message you sent opens a confirmation showing which files will be restored and how
many messages will be dropped. Confirming sends `rewind_files` with that message's UUID, then
restarts the process with `--resume <id> --resume-session-at <uuid>` and puts the original
prompt back in the input field.

Checkpoints cover Write, Edit and NotebookEdit. Changes made through Bash and edits applied by
subagents are not restored.

## Remote control

The Remote control toggle asks the CLI for a session URL and shows it in a dialog with a copy
button. Opening that URL on another device drives the same conversation; the toolbar shows
whether the bridge is connected.

## Authentication

The plugin starts the unmodified `claude` binary as a subprocess and speaks its stream-json
protocol. Sign-in happens through Claude Code's own flow:

```
claude auth login
```

Chisel is not affiliated with Anthropic.

## Requirements

- IntelliJ IDEA 2025.3 or newer, on any platform variant
- JDK 21 for building
- Claude Code CLI on `PATH`, or at `~/.local/bin/claude`, `~/.claude/local/claude` or
  `~/.bun/bin/claude`

## Building

```
./gradlew buildPlugin     # artifact in build/distributions
./gradlew verifyPlugin    # JetBrains Plugin Verifier on the recommended IDEs
./gradlew runIde          # sandbox IDE with the plugin loaded
```

The build compiles against IntelliJ IDEA Ultimate 2025.3, which bundles the Database plugin, and
leaves `untilBuild` open. `Jenkinsfile` runs the build, the verifier and, behind a parameter,
`publishPlugin`.

## Process invocation

```
claude --print --verbose \
  --input-format stream-json --output-format stream-json \
  --include-partial-messages --replay-user-messages --forward-subagent-text \
  --permission-prompt-tool stdio \
  --add-dir <IDE system directory>/chisel \
  --permission-mode plan|manual|auto \
  --allowedTools "Read,Glob,Grep,WebFetch,WebSearch,TodoWrite,Task" \
  --model best|opus|sonnet|haiku|fable \
  --effort low|medium|high|xhigh|max \
  --session-id <uuid>
```

with `CLAUDE_CODE_ENABLE_SDK_FILE_CHECKPOINTING=true` in the environment.

`--permission-prompt-tool stdio` routes permission questions to the host over the control channel
as `can_use_tool` requests. `--permission-prompt-tool`, `--resume-session-at` and `--rewind-files`
do not appear in `claude --help`.

## State on disk

Conversation metadata is stored in `.idea/workspace.xml` under `ChiselConversations`. Transcripts
are written as JSONL under the IDE system directory, one file per conversation. Pasted images and
generated schema files live under `<IDE system directory>/chisel`. Reopening the IDE restores the
conversation list and the conversation that was selected last; the `claude` process starts when
you send the first message in it. Deleting a conversation removes its JSONL file.
