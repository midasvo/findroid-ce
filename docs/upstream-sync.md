# Upstream Sync & CE Release

This playbook has been formalized as a Claude Code skill. The single source of
truth now lives at [`.claude/skills/upstream-sync/SKILL.md`](../.claude/skills/upstream-sync/SKILL.md).

In Claude Code, just say "sync with upstream" or "do a CE release" — the skill
triggers automatically.

## Publishing a beta

Every push to `main` publishes a beta. To publish one by hand:

```bash
# An open pull request, built merged into main
gh workflow run beta.yml -f pr=<number>

# A branch as-is
gh workflow run beta.yml --ref <branch>
```
