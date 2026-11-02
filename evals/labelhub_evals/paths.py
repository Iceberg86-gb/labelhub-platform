"""Repository locations. The harness reads the platform's own contract and config files instead of copying them."""

from __future__ import annotations

from pathlib import Path

EVALS_DIR = Path(__file__).resolve().parent.parent
REPO_ROOT = EVALS_DIR.parent
AI_CONTRACTS_DIR = REPO_ROOT / "packages" / "contracts" / "ai"
API_APPLICATION_YML = REPO_ROOT / "services" / "api" / "src" / "main" / "resources" / "application.yml"
PROMPTS_DIR = EVALS_DIR / "prompts"
DATASETS_DIR = EVALS_DIR / "datasets"
RECORDINGS_DIR = EVALS_DIR / "recordings"
RUNS_DIR = EVALS_DIR / "runs"
CACHE_DIR = EVALS_DIR / ".cache"
