# Contributing

## Checkpoint workflow

Commit and push at meaningful verified checkpoints, not on a timer while files may be broken:

1. Keep each commit focused and free of secrets/student data.
2. Run formatting, unit tests, lint, and a secret scan.
3. Push after the checkpoint passes.
4. Use pull requests once more than one contributor is active.

Suggested early checkpoints: project scaffold, first parser fixture/tests, Room schema, login/session flow, first vertical slice, and each feature module.

## Commit style

Use imperative subjects such as `Add timetable parser fixtures` or `Implement attendance projection`. Do not commit generated APKs, local SDK paths, cookies, credentials, captured live pages, or downloaded course content.

