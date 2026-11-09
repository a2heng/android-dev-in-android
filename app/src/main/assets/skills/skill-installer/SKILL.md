---
name: Skill Installer
description: Install, list or remove opencode skills on this phone from a git repository, zip file or local directory. Use when the user asks to add a skill, or when a new capability is needed as a reusable skill.
---

# Skill Installer

OpenCode discovers skills in `~/.config/opencode/skills/<id>/SKILL.md`.
This skill installs into both the normal config dir and the app's native
config dir (`~/.config/opencode-android/skills`).

## When to use

- The user says "install this skill", "add a skill from GitHub", "make this a skill".
- You need a reusable procedure and want to install it for future sessions.
- The user wants to list or remove installed skills.

## Safety

A skill is **instructions the agent will follow** and can include scripts.
Only install from sources the user trusts. Never install a random URL without
confirming with the user.

## Install

```sh
bash <skill-dir>/scripts/install.sh <git-url|zip-url|local-dir> [name]
```

Examples:

```sh
# GitHub repo (shallow clone)
bash <skill-dir>/scripts/install.sh https://github.com/user/my-skill

# A subdirectory of a repo: name it explicitly
bash <skill-dir>/scripts/install.sh https://github.com/user/skills/tree/main/git-release git-release

# Zip file
bash <skill-dir>/scripts/install.sh https://example.com/skill.zip my-skill

# Local directory
bash <skill-dir>/scripts/install.sh /path/to/skill
```

The script finds `SKILL.md`, validates `name` + `description`, copies the
whole skill directory (including `scripts/`, `references/`, `assets/`), and
prints the installed path. Use `--force` to replace an existing skill.

After installing, start a **new session** (or restart the server) so OpenCode
re-discovers the skill list.

## List

```sh
bash <skill-dir>/scripts/list.sh
```

## Remove

```sh
bash <skill-dir>/scripts/remove.sh <skill-id>
```

This removes the skill from both config directories. It never touches
`android-build` or `skill-installer`.
