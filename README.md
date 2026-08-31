# Chisel

Chisel is an AI coding agent for people who care about the quality of what lands in the
repository. Every change it proposes goes through a review you control: the whole file in a diff,
a comment on any line, and the freedom to rewrite the agent's code by hand before a single byte
touches disk. The corrections you make travel back to the agent and shape the rest of the work.

Chisel runs on your existing Claude Code subscription — it drives the Claude Code CLI already
signed in on your machine, so there is no separate account, no API key and no extra billing.

## Reviewing a write

Nothing is written behind your back. Each write opens in the IDE's side-by-side diff: the file as
it is today on the left, the proposal on the right.

![Commenting a line in the review dialog](screenshots/review-comment.png)

Any line takes a comment, the way you would review a colleague's pull request. Comments stay
pinned to their lines and can pile up across the whole file.

![Editing the proposal by hand](screenshots/review-edit.png)

The proposal is a normal editor as well, so a name, a package or a whole block can be rewritten
in place, with your edits highlighted against what the agent suggested.

Three ways out:

- **Accept** — the file is written as proposed.
- **Adjust** — the write is held back, and your comments and your version of the file go to the
  agent as one correction, with the note that the comments win wherever the two disagree.
- **Reject** — the write is dropped and the turn ends.

A correction is not a one-off. The agent is told to carry the same decisions into every file it
touches afterwards, so a name you fixed once stays fixed.

## Plans, commands and questions

![The plan dialog](screenshots/plan.png)

A plan arrives as a dialog with a feedback field: send it back with notes, or approve it and let
the agent start implementing. Shell commands open with the command and the agent's own
description of what it does, and in Implementation mode the agent is asked to write commands a
reader can follow — one thing at a time, no clever one-liners. Questions from the agent arrive as
a dialog with the options as buttons.

Four modes set how much the agent may do on its own:

| Mode | What happens |
| --- | --- |
| Ask | Read-only. The agent answers, searches and explains; anything that would change the project is refused. |
| Plan | The agent investigates and writes a plan. No file is touched. |
| Implementation | Every write goes through the review dialog, every command through its own dialog. |
| Auto | The agent works on its own and stops only for what it considers risky. |

Modes switch mid-conversation without restarting anything, and the model and the effort level are
picked per conversation.

## Rewind

A message you sent can be rewound. The confirmation lists which files come back and how many
messages disappear; afterwards the project is where it was before that message, and the prompt is
back in the input field ready to be reworded. Writes are covered; changes made through shell
commands are not.

## Conversations

![The prompt input](screenshots/prompt.png)

Conversations live in a list on the left with search, rename and delete. Sessions started in the
terminal in the same project show up there with their history, so work moves between the terminal
and the IDE. The panel on the right shows the selected one: streamed text, collapsible thinking,
tool calls with their results, subagents, and the task list as the agent works through it.
Context usage, token counts and the cost of the conversation sit next to the input.

## Attachments

Files and images go into a prompt by drag and drop, by paste, or through the paperclip. In IDEs
with the Database plugin, a button attaches the schema of a data source as DDL — tables, columns,
keys, indexes and views — so the agent works against the real schema instead of guessing from
migrations. A data source that was never introspected is introspected on the spot.

## Remote control

A conversation can be handed to a phone or another machine: the toolbar gives you a link, and the
same conversation continues there.

## Requirements

- IntelliJ IDEA 2025.3 or newer, any variant
- Claude Code CLI installed and signed in (`claude auth login`)

## Building

```
./gradlew buildPlugin     # artifact in build/distributions
./gradlew verifyPlugin    # JetBrains Plugin Verifier
./gradlew runIde          # sandbox IDE with the plugin loaded
```

Chisel is not affiliated with Anthropic.
