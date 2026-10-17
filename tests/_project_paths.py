from pathlib import Path

TEST_DIR = Path(__file__).resolve().parent
if (TEST_DIR / 'src/main').is_dir() and (TEST_DIR / 'build.gradle').is_file():
    # Development flat module layout used by the implementation worktree.
    PROJECT_ROOT = TEST_DIR
    APP_ROOT = TEST_DIR
    TOOLS_ROOT = TEST_DIR / 'tools'
else:
    # Canonical source archive layout: project/tests + project/app + project/tools.
    PROJECT_ROOT = TEST_DIR.parent
    APP_ROOT = PROJECT_ROOT / 'app'
    TOOLS_ROOT = PROJECT_ROOT / 'tools'
