# github-issue-pr-command

# GitHub Workflow Automation Protocol

When I ask to "ship it", "deploy feature", or "start github workflow" regarding the current code implementation, you must strictly follow this sequence of actions using GitHub MCP tools and Terminal commands:

## Step 1: Create GitHub Issue (MCP)
1. Analyze the current code changes and summarize them.
2. Use the GitHub MCP tool to create a new issue.
   - **Title**: A concise summary of the feature or fix.
   - **Body**: A detailed description of the implementation.
3. **IMPORTANT**: Remember the returned `issue_number`.

## Step 2: Create & Switch Branch (Terminal)
1. Generate a branch name using the format: `feature/issue-{issue_number}` (or `fix/issue-{issue_number}` depending on context).
2. Propose the terminal command:
   ```bash
   git checkout -b feature/issue-{issue_number}

## Step 3: Commit Changes (Terminal)
Propose the terminal commands to stage and commit:

git add .
git commit -m "feat: {issue_title} (#{issue_number})"
Note: The commit message title MUST end with (#{issue_number}).

## Step 4: Push Branch (Terminal)
Propose the terminal command to push the new branch:

git push -u origin feature/issue-{issue_number}


## Step 5: Create Pull Request (MCP)
After the push is successful, use the GitHub MCP tool to create a Pull Request.

Title: Same as the issue title or slightly more descriptive.
Body: "Closes #{issue_number}\n\n## Description\n{summary_of_changes}"
Head: The new branch name (feature/issue-{issue_number}).
Base: main (or develop, ask if unsure).

Execution Rules:

Execute Step 1 (Issue) first.
Wait for the user to run the Terminal commands in Steps 2, 3, and 4.
Once the push is confirmed, execute Step 5 (PR).