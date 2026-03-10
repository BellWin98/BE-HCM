# github-issue-pr-command

# GitHub Workflow Automation Protocol

When I ask to "ship it", "deploy feature", or "start github workflow" regarding the current code implementation, you must **automatically execute** the full sequence (Step 1 through 5) using **GitHub MCP** (server: `user-github`) and Terminal commands—without pausing for the user to run commands manually.

---

## Step 1: Create GitHub Issue (MCP)

1. Analyze the current code changes and summarize them.
2. Resolve **owner** and **repo**: run `git remote -v` and parse the origin URL (e.g. `https://github.com/BellWin98/BE-HCM.git` → owner: `BellWin98`, repo: `BE-HCM`).
3. Call MCP tool **create_issue** (**모든 텍스트 필드 `title`, `body`는 반드시 한국어로 작성할 것**):
   - **Server**: `user-github`
   - **Tool**: `create_issue`
   - **Arguments**:
     - `owner` (string): repository owner
     - `repo` (string): repository name
     - `title` (string): concise summary of the feature or fix **(Korean only)**
     - `body` (string): detailed description of the implementation **(Korean only)**
4. **IMPORTANT**: Remember the returned `number` (issue_number) from the response.

---

## Step 2: Create & Switch Branch (Terminal)

**Execute** the terminal command:

```bash
git checkout -b feature/issue-{issue_number}
```

Use `fix/issue-{issue_number}` for bugfixes if appropriate.

---

## Step 3: Commit Changes (Terminal)

**Execute** the terminal commands:

```bash
git add .
git commit -m "feat: {issue_title_in_korean} (#{issue_number})"
```

**Note**: The commit message MUST end with `(#{issue_number})` and the message text (including `feat: {issue_title_in_korean}`) MUST be written in Korean. Optionally use `git add <paths>` to commit only modified source files.

---

## Step 4: Push Branch (Terminal)

**Execute** the terminal command (requires `network` and `git_write` permissions):

```bash
git push -u origin feature/issue-{issue_number}
```

---

## Step 5: Create Pull Request (MCP)

**Execute immediately** after Step 4 succeeds. Call MCP tool **create_pull_request**:

- **Server**: `user-github`
- **Tool**: `create_pull_request`
- **Arguments** (**`title`, `body`는 반드시 한국어로 작성할 것**):
  - `owner` (string): same as Step 1
  - `repo` (string): same as Step 1
  - `title` (string): same as the issue title (or slightly more descriptive) **(Korean only)**
  - `body` (string): `Closes #{issue_number}\n\n## Description\n{summary_of_changes_in_korean}` **(Korean only)**
  - `head` (string): branch name, e.g. `feature/issue-{issue_number}`
  - `base` (string): `dev` if the branch exists, otherwise `main`

---

## Execution Rules

1. **Run all steps automatically in order** (Step 1 → 2 → 3 → 4 → 5). Do not stop to ask the user to run commands.
2. Execute **Step 1** (create issue via MCP; remember `issue_number` and `title`).
3. Execute **Step 2** (create/switch branch via terminal).
4. Execute **Step 3** (stage and commit via terminal; use `git_write` permission for commit).
5. Execute **Step 4** (push via terminal; use `network` and `git_write` permissions).
6. Execute **Step 5** (create PR via MCP). If `base: "dev"` fails with invalid base, retry with `base: "main"`.
7. At the end, report the created issue URL, PR URL, and branch name to the user.
