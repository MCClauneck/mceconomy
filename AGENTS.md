# Agent Instructions & Workflow

**Strict Rules & Execution**
* **Read First (Structure):** Always read `INDEX.md` to understand the project structure. If `INDEX.md` does not exist, create it first. Actively update it when structural changes occur.
* **Read Second (Context):** Always read `README.md` to understand the core project goals, context, and setup instructions.
* **Skills:** Evaluate tasks against the skills defined in the `.skills/` directory and strictly follow the execution steps defined there.
* **Execution:** Create a solid plan. Write code section-by-section. Test thoroughly and fix any errors. Verify code security for modified files before completing the task.
* **Modularity:** Separate code into multiple focused files and modules to prevent spaghetti code. Keep files concise and adhere to the Single Responsibility Principle.
* **No Master/Main:** Never work directly on `main` or `master`. Create a new branch if the task scope changes; otherwise, continue on the active branch.
* **Version:** The version must use the "Major.Minor.Patch" format. If it does not, change it to this format. Before merging, check if the version has been updated. If it hasn't changed, ask the user if they want to update it. If they answer yes, update the version according to the standard definitions of Major, Minor, and Patch. Other acceptable formats include `major.minor.patch-{type}` (e.g., `1.0.0-beta`).
* **No Links:** Never include links in commit and PR titles or descriptions.
* **Task:** If the user provides one or multiple tasks, each task must have its own branch created, a pull request (PR) opened, and be merged separately.
* **Requirement:** Each repository must document its requirements within the `README.md` file, placed at or near the end of the document. This should include details such as the required environment, engine versions (e.g., Node.js, JVM, Python), or specify if the repository is simply an extension for a specific software or platform.
* **Website:** If the user has also cloned the website repository, the agents must update the website contents as well.
* **Branch:** If a branch is created and does not follow the convention, you must recreate it. However, if you are unable to do so, provide the user with a choice of how to proceed.

**Agent Directories**
* `.agents/`: Agent configurations and specific behaviors.
* `.skills/`: Specific skill definitions and execution steps.
* `.tools/`: Tool definitions and schemas.
* `.knowledge/`: Domain-specific context.
* `.personas/`: Specific roles to adopt.
* `.ethics/`: Safety bounds and constraints.
*(Note: Keep universal rules in `AGENTS.md` at the root. All other instructions, context, and configurations must be placed inside these directories. Suggest changes to the user before applying them.)*

**Git & Branching Workflow (STRICT)**
* **Branch Naming:** Must follow `{type}/{primary-noun}` (e.g., `feat/login`). Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`, `ci`, `chore`, `revert`.
* **Commit Frequency & Verification:** Commit each change or group related commits. Do not wait for the entire session to finish. Always check the diff before creating a commit.
* **Commits:** Must use Conventional Commits (`type[optional scope]: description`). Commit messages must be plain text with no links or Jira IDs.
* **Pull Requests (PR):** Open sequentially. Always ask for user approval first. Provide a detailed report of added/modified/deleted features in the PR body. Assume any references to github.com or gitlab.com are for their cloud-hosted environments.
