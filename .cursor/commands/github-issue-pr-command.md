# github-issue-pr-command

# GitHub Workflow Automation Protocol

When I ask to "ship it", "deploy feature", or "start github workflow" regarding the current code implementation, you must strictly follow this sequence of actions using **GitHub MCP** (server: `user-github`) and Terminal commands.

---

## Step 1: Create GitHub Issue (MCP)

1. Analyze the current code changes and summarize them.
2. Resolve **owner** and **repo**: run `git remote -v` and parse the origin URL (e.g. `https://github.com/BellWin98/BE-HCM.git` → owner: `BellWin98`, repo: `BE-HCM`).
3. Call MCP tool **create_issue**:
   - **Server**: `user-github`
   - **Tool**: `create_issue`
   - **Arguments**:
     - `owner` (string): repository owner
     - `repo` (string): repository name
     - `title` (string): concise summary of the feature or fix
     - `body` (string): detailed description of the implementation
4. **IMPORTANT**: Remember the returned `number` (issue_number) from the response.

---

## Step 2: Create & Switch Branch (Terminal)

Propose the terminal command (user runs it):

```bash
git checkout -b feature/issue-{issue_number}
```

Use `fix/issue-{issue_number}` for bugfixes if appropriate.

---

## Step 3: Commit Changes (Terminal)

Propose the terminal commands (user runs them):

```bash
git add .
git commit -m "feat: {issue_title} (#{issue_number})"
```

**Note**: The commit message MUST end with `(#{issue_number})`.

---

## Step 4: Push Branch (Terminal)

Propose the terminal command (user runs it):

```bash
git push -u origin feature/issue-{issue_number}
```

---

## Step 5: Create Pull Request (MCP)

After the user confirms the push, call MCP tool **create_pull_request**:

- **Server**: `user-github`
- **Tool**: `create_pull_request`
- **Arguments**:
  - `owner` (string): same as Step 1
  - `repo` (string): same as Step 1
  - `title` (string): same as the issue title (or slightly more descriptive)
  - `body` (string): `Closes #{issue_number}\n\n## Description\n{summary_of_changes}`
  - `head` (string): branch name, e.g. `feature/issue-{issue_number}`
  - `base` (string): `main` (or `develop` — ask if unsure)

---

## Execution Rules

1. Execute **Step 1** first (create issue via MCP; remember issue_number).
2. Propose **Steps 2, 3, 4** terminal commands and wait for the user to run them.
3. Once the user confirms push, execute **Step 5** (create PR via MCP).
