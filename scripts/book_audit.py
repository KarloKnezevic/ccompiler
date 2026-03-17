#!/usr/bin/env python3
"""
Book completeness audit for docs/.

Checks:
- Word counts (global + per chapter)
- Mermaid block counts and diagram subtype counts
- Presence of pseudocode/code blocks and worked examples
- High-level chapter coverage
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path

DOCS_DIR = Path("docs")

REQUIRED_CHAPTERS = [
    "00_frontmatter",
    "01_introduction",
    "02_compiler_theory",
    "03_lexer",
    "04_parser",
    "05_semantic_analysis",
    "06_ir",
    "07_optimizations",
    "08_codegen_frisc",
    "09_runtime",
    "10_simulator",
    "11_real_world_programs",
    "12_performance",
    "13_future_work",
    "appendix",
]

MERMAID_BLOCK_RE = re.compile(r"```mermaid\s+([\s\S]*?)```", re.MULTILINE)
CODE_BLOCK_RE = re.compile(r"```([\w+-]*)\s*\n([\s\S]*?)```", re.MULTILINE)
WORD_RE = re.compile(r"\b[\w][\w'\-]*\b", re.UNICODE)

TARGET_TOTAL_WORDS = 350_000
TARGET_CHAPTER_WORDS = 25_000
TARGET_SUBCHAPTER_WORDS = 5_000


@dataclass
class ChapterMetrics:
    name: str
    words: int = 0
    file_word_counts: dict[str, int] = field(default_factory=dict)
    mermaid_blocks: int = 0
    class_diagrams: int = 0
    sequence_diagrams: int = 0
    state_diagrams: int = 0
    flow_diagrams: int = 0
    pseudocode_blocks: int = 0
    worked_examples: int = 0
    cross_refs: int = 0
    complexity_sections: int = 0


def count_words(text: str) -> int:
    return len(WORD_RE.findall(text))


def classify_mermaid(block: str) -> tuple[int, int, int, int]:
    stripped = block.lstrip()
    flow = 0
    cls = 0
    seq = 0
    state = 0
    if stripped.startswith("flowchart") or stripped.startswith("graph"):
        flow = 1
    if stripped.startswith("classDiagram"):
        cls = 1
    if stripped.startswith("sequenceDiagram"):
        seq = 1
    if stripped.startswith("stateDiagram"):
        state = 1
    return flow, cls, seq, state


def audit_chapter(chapter_dir: Path) -> ChapterMetrics:
    metrics = ChapterMetrics(name=chapter_dir.name)
    for md_file in sorted(chapter_dir.glob("*.md")):
        text = md_file.read_text(encoding="utf-8")
        file_words = count_words(text)
        metrics.words += file_words
        metrics.file_word_counts[str(md_file)] = file_words

        mermaid_blocks = MERMAID_BLOCK_RE.findall(text)
        metrics.mermaid_blocks += len(mermaid_blocks)
        for block in mermaid_blocks:
            flow, cls, seq, state = classify_mermaid(block)
            metrics.flow_diagrams += flow
            metrics.class_diagrams += cls
            metrics.sequence_diagrams += seq
            metrics.state_diagrams += state

        code_blocks = CODE_BLOCK_RE.findall(text)
        for lang, body in code_blocks:
            lowered_lang = (lang or "").strip().lower()
            lowered_body = body.lower()
            if lowered_lang in {"pseudocode", "pseudo"}:
                metrics.pseudocode_blocks += 1
            elif "procedure " in lowered_body or "for each" in lowered_body:
                metrics.pseudocode_blocks += 1

        metrics.worked_examples += len(re.findall(r"(?i)worked\s+example|case\s+study", text))
        metrics.cross_refs += len(re.findall(r"(?i)compiler-lexer|compiler-parser|compiler-semantics|compiler-ir|compiler-opt|compiler-codegen-frisc", text))
        metrics.complexity_sections += len(re.findall(r"(?i)complexity|performance analysis|instruction count", text))

    return metrics


def main() -> None:
    missing = [c for c in REQUIRED_CHAPTERS if not (DOCS_DIR / c).is_dir()]
    if missing:
        print("Missing required chapter directories:")
        for chapter in missing:
            print(f"  - {chapter}")
        raise SystemExit(1)

    all_metrics = []
    for chapter in REQUIRED_CHAPTERS:
        chapter_dir = DOCS_DIR / chapter
        all_metrics.append(audit_chapter(chapter_dir))

    total_words = sum(m.words for m in all_metrics)

    print("=== Book Audit Report ===")
    print(f"Docs root: {DOCS_DIR}")
    print(f"Total words: {total_words}")
    print(f"Target total words: {TARGET_TOTAL_WORDS}")
    print()

    for m in all_metrics:
        print(f"[{m.name}]")
        print(f"  words: {m.words}")
        print(f"  mermaid blocks: {m.mermaid_blocks}")
        print(f"  flow/class/sequence/state: {m.flow_diagrams}/{m.class_diagrams}/{m.sequence_diagrams}/{m.state_diagrams}")
        print(f"  pseudocode blocks: {m.pseudocode_blocks}")
        print(f"  worked examples and case studies markers: {m.worked_examples}")
        print(f"  module cross-reference markers: {m.cross_refs}")
        print(f"  complexity/performance markers: {m.complexity_sections}")

        if m.name not in {"00_frontmatter", "appendix"}:
            if m.words < TARGET_CHAPTER_WORDS:
                print(f"  deficit: chapter words below target by {TARGET_CHAPTER_WORDS - m.words}")

            short_files = [
                (path, words)
                for path, words in m.file_word_counts.items()
                if words < TARGET_SUBCHAPTER_WORDS
            ]
            if short_files:
                print("  subchapter deficits (<5000 words):")
                for path, words in short_files:
                    print(f"    - {path}: {words}")

        print()

    if total_words < TARGET_TOTAL_WORDS:
        print(f"GLOBAL DEFICIT: {TARGET_TOTAL_WORDS - total_words} words remaining to target.")


if __name__ == "__main__":
    main()
