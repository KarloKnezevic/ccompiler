#!/usr/bin/env python3
"""
generate_book.py — Automated LaTeX book generator for the FRISCcc compiler project.

Reads documentation from docs/, runs compiler examples, generates benchmarks,
and produces a professional 500+ page LaTeX/PDF book.

Usage:
    python3 generate_book.py           # Full build (compile examples + generate book + PDF)
    python3 generate_book.py --tex     # Generate .tex only (skip PDF compilation)
    python3 generate_book.py --quick   # Skip compiler examples (use cached if available)
"""

import os
import re
import sys
import json
import time
import shutil
import hashlib
import argparse
import subprocess
import textwrap
from pathlib import Path
from typing import Optional

# ===========================================================================
# Configuration
# ===========================================================================

PROJECT_ROOT = Path(__file__).parent.resolve()
DOCS_DIR = PROJECT_ROOT / "docs"
BOOK_DIR = PROJECT_ROOT / "book"
CHAPTERS_DIR = BOOK_DIR / "chapters"
RES_DIR = BOOK_DIR / "res"
EXAMPLES_DIR = PROJECT_ROOT / "examples"
CONFIG_DIR = PROJECT_ROOT / "config"
COMPILER_JAR = PROJECT_ROOT / "cli" / "target" / "ccompiler.jar"
COMPILER_BIN = PROJECT_ROOT / "compiler-bin"
CACHE_DIR = BOOK_DIR / ".cache"

# Tools — resolved at startup
PANDOC = shutil.which("pandoc") or "/usr/local/bin/pandoc"
PDFLATEX = (shutil.which("pdflatex")
            or "/usr/local/texlive/2021/bin/universal-darwin/pdflatex")
MAKEINDEX = (shutil.which("makeindex")
             or "/usr/local/texlive/2021/bin/universal-darwin/makeindex")
JAVA = shutil.which("java") or "/usr/bin/java"
MMDC = shutil.which("mmdc")  # Optional: Mermaid CLI
INKSCAPE = shutil.which("inkscape")  # Optional: SVG→PDF conversion

# Chapter order — only dirs that exist are included
CHAPTER_ORDER = [
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

# Files to include from each chapter dir (in order). Files numbered 96-101
# are supplementary and excluded from the main flow to keep the book focused.
# Also exclude very large artifact atlases (04, 05, 06, 07, 08 in real_world).
EXCLUDED_FILE_PREFIXES = ("96_", "97_", "98_", "99_", "100_", "101_")
EXCLUDED_FILES = {
    "04_extended_artifact_atlas.md",
    "05_large_regression_artifact_atlas.md",
    "06_additional_cases_040.md",
    "07_additional_cases_060.md",
    "08_additional_cases_012.md",
    # Glossary is generated separately as a formatted longtable
    "01_glossary.md",
}

# Maps chapter dirs to human-readable titles
CHAPTER_TITLES = {
    "00_frontmatter":          None,  # no chapter heading — frontmatter
    "01_introduction":         "Introduction",
    "02_compiler_theory":      "Compiler Architecture and Theory",
    "03_lexer":                "Lexical Analysis",
    "04_parser":               "Syntax Analysis",
    "05_semantic_analysis":    "Semantic Analysis",
    "06_ir":                   "Intermediate Representation",
    "07_optimizations":        "Optimization Passes",
    "08_codegen_frisc":        "Code Generation for FRISC",
    "09_runtime":              "Runtime Support and ABI",
    "10_simulator":            "FRISC Simulator Integration",
    "11_real_world_programs":  "Real-World Programs and Case Studies",
    "12_performance":          "Performance Engineering",
    "13_future_work":          "Future Work and Research Directions",
    "appendix":                "Appendices",
}

# Example programs for per-chapter demonstrations
CHAPTER_EXAMPLES = {
    "03_lexer": [
        ("examples/fer/e_001/program.c", "Minimal program"),
        ("examples/fer/e_005/program.c", "Arithmetic expression"),
    ],
    "04_parser": [
        ("examples/valid/control_flow/0180_21_for_loop_semantics/program.c", "For loop"),
        ("examples/valid/basics/0001_basics_program40/program.c", "Nested function calls"),
    ],
    "05_semantic_analysis": [
        ("examples/valid/arrays/0178_15_array_param_decay/program.c", "Array parameter decay"),
        ("examples/valid/basics/0001_basics_program40/program.c", "Function call chain"),
    ],
    "06_ir": [
        ("examples/real_world/math_fibonacci_iter/program.c", "Fibonacci (iterative)"),
        ("examples/fer/e_001/program.c", "Minimal program IR"),
    ],
    "07_optimizations": [
        ("examples/real_world/real_prime_sieve/program.c", "Prime sieve"),
    ],
    "08_codegen_frisc": [
        ("examples/real_world/math_gcd_lcm/program.c", "GCD/LCM"),
        ("examples/fer/e_005/program.c", "Simple arithmetic FRISC"),
    ],
    "11_real_world_programs": [
        ("examples/real_world/real_bfs_shortest_path/program.c", "BFS shortest path"),
        ("examples/real_world/real_quicksort_max/program.c", "Quicksort"),
        ("examples/real_world/eng_dijkstra_shortest_path/program.c", "Dijkstra"),
    ],
}

# Programs for the benchmark chapter
BENCHMARK_PROGRAMS = [
    "examples/real_world/real_prime_sieve/program.c",
    "examples/real_world/math_fibonacci_iter/program.c",
    "examples/real_world/real_bfs_shortest_path/program.c",
    "examples/real_world/real_quicksort_max/program.c",
    "examples/real_world/real_knapsack_dp/program.c",
    "examples/real_world/real_dot_product/program.c",
    "examples/real_world/math_gcd_lcm/program.c",
    "examples/real_world/math_matrix_vector/program.c",
    "examples/real_world/eng_dijkstra_shortest_path/program.c",
    "examples/real_world/math_polynomial_horner/program.c",
]

# ===========================================================================
# Regex patterns
# ===========================================================================

MERMAID_BLOCK_RE = re.compile(r"```mermaid\s+([\s\S]*?)```", re.MULTILINE)
CODE_BLOCK_RE = re.compile(r"```(\w+)?\s*\n(.*?)```", re.DOTALL | re.MULTILINE)

# Matches AI-generated filler blocks: "### Reinforcement Unit N" + identical paragraphs
REINFORCEMENT_RE = re.compile(
    r"^#{2,4}\s+Reinforcement Unit\s+\d+\s*\n"
    r"(?:(?!^#{1,6}\s).*\n)*",
    re.MULTILINE,
)
HEADING_RE = re.compile(r"^(#{1,6})\s+(.+)$", re.MULTILINE)
MARKDOWN_LINK_RE = re.compile(
    r"\[([^\]]+)\]\(([^)]+\.md(?:#[^)]+)?)\)", re.MULTILINE
)
TABLE_RE = re.compile(
    r"^\|(.+)\|\s*\n\|[-:| ]+\|\s*\n((?:\|.+\|\s*\n)*)",
    re.MULTILINE
)

# ===========================================================================
# Utility functions
# ===========================================================================


def slugify(text: str) -> str:
    text = text.strip().replace(" ", "-").replace("\t", "-")
    return re.sub(r"[^0-9A-Za-z_\-]+", "-", text).strip("-").lower()


def run_cmd(cmd, timeout=60, cwd=None):
    """Run a command, return (returncode, stdout, stderr)."""
    try:
        r = subprocess.run(
            cmd,
            capture_output=True,
            text=False,
            timeout=timeout,
            cwd=cwd,
        )
        stdout = r.stdout.decode("utf-8", errors="replace") if r.stdout else ""
        stderr = r.stderr.decode("utf-8", errors="replace") if r.stderr else ""
        return r.returncode, stdout, stderr
    except subprocess.TimeoutExpired:
        return -1, "", "TIMEOUT"
    except FileNotFoundError:
        return -1, "", f"NOT FOUND: {cmd[0]}"


def file_hash(path: Path) -> str:
    return hashlib.md5(path.read_bytes()).hexdigest()[:12]


# ===========================================================================
# Compiler runner — generates artifacts for book examples
# ===========================================================================

class CompilerRunner:
    """Runs the FRISCcc compiler on example programs and caches results."""

    def __init__(self, jar_path: Path, cache_dir: Path):
        self.jar = jar_path
        self.cache_dir = cache_dir
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def _cache_key(self, source: Path, flags: list) -> str:
        h = hashlib.md5()
        h.update(source.read_bytes())
        h.update("|".join(flags).encode())
        return h.hexdigest()[:16]

    def _cache_path(self, key: str) -> Path:
        return self.cache_dir / key

    def compile(self, source: Path, flags: list, force=False) -> dict:
        """
        Compile a source file and return dict of artifacts:
        {tokens, ast, ir, frisc, ir_lines, frisc_lines}
        """
        key = self._cache_key(source, flags)
        cache_file = self._cache_path(key) / "result.json"

        if not force and cache_file.exists():
            try:
                return json.loads(cache_file.read_text())
            except Exception:
                pass

        # Run compiler
        cmd = [JAVA, "-jar", str(self.jar)] + flags + [str(source)]
        rc, stdout, stderr = run_cmd(cmd, timeout=30)

        result = {
            "source": str(source),
            "flags": flags,
            "returncode": rc,
            "stdout": stdout[:2000],
            "stderr": stderr[:2000],
            "tokens": "",
            "ast": "",
            "ir": "",
            "frisc": "",
            "ir_lines": 0,
            "frisc_lines": 0,
        }

        # Read artifacts from compiler-bin/
        for name, field in [
            ("tokens.txt", "tokens"),
            ("ast.txt", "ast"),
            ("intermediate.ir", "ir"),
            ("a.out", "frisc"),
        ]:
            artifact = COMPILER_BIN / name
            if artifact.exists():
                content = artifact.read_text(encoding="utf-8", errors="replace")
                result[field] = content
                result[f"{field}_lines"] = len(content.splitlines())

        result["ir_lines"] = len(result["ir"].splitlines())
        result["frisc_lines"] = len(result["frisc"].splitlines())

        # Cache
        cp = self._cache_path(key)
        cp.mkdir(parents=True, exist_ok=True)
        cache_file.write_text(json.dumps(result, indent=2))

        return result

    def benchmark(self, source: Path) -> dict:
        """Compile with O0 and O1, return instruction counts."""
        r0 = self.compile(source, ["--O0", "--all"])
        r1 = self.compile(source, ["--O1", "--all"])

        return {
            "program": source.parent.name,
            "source": str(source),
            "o0_ir_lines": r0["ir_lines"],
            "o1_ir_lines": r1["ir_lines"],
            "o0_frisc_lines": r0["frisc_lines"],
            "o1_frisc_lines": r1["frisc_lines"],
            "ir_reduction": (
                round(
                    (1 - r1["ir_lines"] / max(r0["ir_lines"], 1)) * 100, 1
                )
                if r0["ir_lines"] > 0
                else 0
            ),
            "frisc_reduction": (
                round(
                    (1 - r1["frisc_lines"] / max(r0["frisc_lines"], 1)) * 100,
                    1,
                )
                if r0["frisc_lines"] > 0
                else 0
            ),
        }


# ===========================================================================
# Mermaid diagram handling
# ===========================================================================

def strip_filler_content(markdown: str) -> str:
    """Remove AI-generated filler blocks (Reinforcement Units etc.)."""
    cleaned = REINFORCEMENT_RE.sub("", markdown)
    # Collapse runs of 3+ blank lines into 2
    cleaned = re.sub(r"\n{4,}", "\n\n\n", cleaned)
    return cleaned


def sanitize_mermaid_code(code: str) -> str:
    """Fix common Mermaid parsing issues."""
    code = re.sub(r"<br\s*/?>", " ", code, flags=re.IGNORECASE)
    code = code.replace("&nbsp;", " ")
    code = code.replace("&lt;", "<")
    code = code.replace("&gt;", ">")
    code = code.replace("&amp;", "&")
    return code


def render_mermaid_svg(mmd_code: str, output_svg: Path) -> bool:
    """Render Mermaid code to SVG and PDF using mmdc. Returns True on success."""
    if not MMDC:
        return False

    mmd_path = output_svg.with_suffix(".mmd")
    mmd_path.write_text(mmd_code, encoding="utf-8")

    # Render SVG (for archival and fallback)
    rc, _, stderr = run_cmd(
        [MMDC, "-i", str(mmd_path), "-o", str(output_svg), "-b", "white"],
        timeout=30,
    )
    if rc != 0 or not output_svg.exists():
        return False

    # Render directly to PDF using mmdc's Puppeteer backend.
    # This avoids Inkscape's <foreignObject> rendering bug that produces
    # black rectangles. Puppeteer uses Chromium which renders SVG correctly.
    pdf_path = output_svg.with_suffix(".pdf")
    rc2, _, _ = run_cmd(
        [MMDC, "-i", str(mmd_path), "-o", str(pdf_path),
         "-b", "white", "--pdfFit"],
        timeout=30,
    )

    return True


def extract_and_replace_mermaid(
    markdown: str, prefix: str, res_dir: Path
) -> tuple:
    """Replace ```mermaid``` blocks with image references."""
    figure_info = []
    counter = [0]

    def repl(match):
        counter[0] += 1
        mermaid_code = sanitize_mermaid_code(match.group(1).strip()) + "\n"
        base_name = f"{prefix}_diag{counter[0]:03d}"
        svg_path = res_dir / f"{base_name}.svg"

        rendered = render_mermaid_svg(mermaid_code, svg_path)

        # Prefer PDF (generated by inkscape) over SVG for pdflatex
        pdf_path = res_dir / f"{base_name}.pdf"
        if rendered and pdf_path.exists():
            img_rel = Path("res") / f"{base_name}.pdf"
        elif rendered:
            img_rel = Path("res") / f"{base_name}.svg"
        else:
            img_rel = None

        figure_info.append({
            "base_name": base_name,
            "svg_path": Path("res") / f"{base_name}.svg",
            "rendered": rendered,
            "mermaid_code": mermaid_code,
        })

        if img_rel:
            return f"![]({img_rel})"
        else:
            # Fallback: omit diagram with a note instead of dumping raw code
            return "*[Diagram omitted — Mermaid CLI unavailable]*"

    processed = MERMAID_BLOCK_RE.sub(repl, markdown)
    return processed, figure_info


# ===========================================================================
# Markdown preprocessing
# ===========================================================================

def build_md_label_mapping(docs_dir: Path) -> dict:
    """Build mapping from .md file paths to LaTeX label IDs."""
    mapping = {}
    for chapter_dir in sorted(docs_dir.iterdir()):
        if not chapter_dir.is_dir():
            continue
        dir_name = chapter_dir.name
        for md_file in sorted(chapter_dir.glob("*.md")):
            rel = str(md_file.relative_to(docs_dir)).replace("\\", "/")
            label = f"sec:{dir_name}-{md_file.stem}"
            mapping[rel] = label
            mapping[f"../{rel}"] = label
            mapping[f"./{rel}"] = label
    return mapping


def preprocess_markdown(
    markdown: str, label_mapping: dict, chapter_id: str
) -> str:
    """Preprocess markdown before Pandoc conversion."""
    content = markdown
    label_counts = {}

    # Normalize code fences
    content = re.sub(r"```(assembly|asm)\s*\n", r"```frisc\n", content, flags=re.MULTILINE)
    content = re.sub(r"```pseudo\s*\n", r"```pseudocode\n", content, flags=re.MULTILINE)

    # Ensure blank lines before bullet lists
    lines = content.split("\n")
    processed = []
    for i, line in enumerate(lines):
        processed.append(line)
        if (
            i > 0
            and line.strip().startswith("- ")
            and lines[i - 1].strip()
            and not lines[i - 1].strip().startswith("- ")
        ):
            if not lines[i - 1].rstrip().endswith((":", ";", ",")):
                processed.insert(-1, "")
    content = "\n".join(processed)

    # Convert .md links to cross-references
    def replace_link(match):
        text = match.group(1)
        target = match.group(2)
        file_path = target.split("#")[0].replace("\\", "/")
        if file_path in label_mapping:
            label = label_mapping[file_path]
            return f"[{text}](#{label})"
        return match.group(0)

    content = MARKDOWN_LINK_RE.sub(replace_link, content)

    # Add labels to headings
    def add_heading_label(match):
        level = len(match.group(1))
        text = match.group(2).strip()
        if re.search(r"\{#[^}]+\}\s*$", text):
            return match.group(0)  # already has label

        heading_slug = slugify(text)
        label_base = f"sec:{chapter_id}-{heading_slug}"
        count = label_counts.get(label_base, 0)
        label_counts[label_base] = count + 1
        label = label_base if count == 0 else f"{label_base}-{count + 1}"

        return f"{match.group(1)} {text} {{#{label}}}"

    content = HEADING_RE.sub(add_heading_label, content)

    return content


# ===========================================================================
# LaTeX helpers
# ===========================================================================

def find_matching_brace(text: str, open_pos: int) -> int:
    """Find the closing brace that matches the opening brace at open_pos.

    Returns the index of the matching '}', or -1 if not found.
    """
    depth = 0
    i = open_pos
    while i < len(text):
        if text[i] == '{':
            depth += 1
        elif text[i] == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def extract_section_heading(text: str, pos: int) -> tuple:
    """Extract the full section heading text at pos (after \\section{).

    Returns (full_match_end, inner_text) or (None, None) if not matched.
    """
    if pos >= len(text) or text[pos] != '{':
        return None, None
    close = find_matching_brace(text, pos)
    if close == -1:
        return None, None
    return close + 1, text[pos + 1 : close]


def sanitize_caption(text: str) -> str:
    """Sanitize LaTeX heading text for use as a figure/index caption."""
    # Remove \texorpdfstring{...}{alt} -> use the alt text
    while r'\texorpdfstring' in text:
        idx = text.find(r'\texorpdfstring')
        # Skip past \texorpdfstring
        rest = text[idx + len(r'\texorpdfstring'):]
        # Find first arg
        if rest and rest[0] == '{':
            close1 = find_matching_brace(rest, 0)
            if close1 != -1:
                rest2 = rest[close1 + 1:]
                if rest2 and rest2[0] == '{':
                    close2 = find_matching_brace(rest2, 0)
                    if close2 != -1:
                        alt_text = rest2[1:close2]
                        text = text[:idx] + alt_text + rest2[close2 + 1:]
                        continue
        break  # bail if we can't parse it

    # Remove \texttt{...} -> contents
    text = re.sub(r'\\texttt\{([^{}]*)\}', r'\1', text)
    # Remove other LaTeX commands with single arg
    text = re.sub(r'\\[a-zA-Z]+\{([^{}]*)\}', r'\1', text)
    # Remove stray backslash commands (e.g. \textgreater, \par)
    text = re.sub(r'\\[a-zA-Z]+', '', text)
    # Remove braces and remaining backslashes (all commands already stripped)
    text = re.sub(r'[{}\\]', '', text)
    # Now escape LaTeX specials for safe use in \caption{} and \index{}
    text = text.replace('_', r'\_')
    text = text.replace('&', r'\&')
    text = text.replace('#', r'\#')
    text = text.replace('%', r'\%')
    text = re.sub(r'\s+', ' ', text).strip()
    # Truncate long captions
    if len(text) > 120:
        text = text[:120].rstrip() + '...'
    return text


# ===========================================================================
# LaTeX post-processing
# ===========================================================================

def post_process_latex(tex_file: Path, chapter_id: str):
    """Post-process generated LaTeX for professional quality."""
    content = tex_file.read_text(encoding="utf-8")
    original = content

    # --- Remove redundant chapter-title sections ---
    # The markdown files have "# Chapter N. Title" which pandoc turns into
    # \section{Chapter N. Title}. Since \chapter{} is added by create_main_tex,
    # this first section is redundant and creates ugly double numbering.
    content = re.sub(
        r"\\section\{(?:Chapter\s+\d+[\.:]\s*|Appendix\s+[A-Z][\.:]\s*)?([^}]+)\}\s*"
        r"(?:\\index\{[^}]*\}\s*)?"
        r"(?:\\label\{[^}]*\})?",
        lambda m: f"% Chapter title section removed (handled by \\chapter{{}})\n"
                  f"\\label{{sec:{chapter_id}}}",
        content,
        count=1,  # only remove the first one
    )

    # --- Strip manual numbering from section/subsection titles ---
    # The markdown headings have "## 6.1 Title" which creates duplicate numbering
    # when LaTeX auto-numbers them as "6.1 6.1 Title". Strip the manual prefix.
    def strip_heading_number(match):
        cmd = match.group(1)  # e.g., \section, \subsection or \subsubsection
        title = match.group(2)
        # Strip "Chapter N. " or "Chapter N Appendix. " prefix
        cleaned = re.sub(r"^Chapter\s+\d+(?:\s+Appendix)?[\.:]\s*", "", title)
        # Strip "Appendix A. " prefix
        cleaned = re.sub(r"^Appendix\s+[A-Z][\.:]\s*", "", cleaned)
        # Strip patterns like "6.1 ", "6.1.1 ", "H.2 ", "A.3.1 ", etc.
        cleaned = re.sub(r"^\d+\.\d+(?:\.\d+)?\s+", "", cleaned)
        cleaned = re.sub(r"^[A-Z]\.\d+(?:\.\d+)?\s+", "", cleaned)
        return f"{cmd}{{{cleaned}}}"

    content = re.sub(
        r"(\\(?:sub)?(?:sub)?section)\{([^}]+)\}",
        strip_heading_number,
        content,
    )

    # --- Fix lstinline that crosses EOL (replace with texttt) ---
    # Only convert multi-line lstinline to texttt; single-line ones work fine
    def fix_multiline_lstinline(match):
        delim = match.group(1)
        inner = match.group(2)
        if "\n" not in inner:
            return match.group(0)  # Single-line: leave as-is
        # Multi-line: convert to texttt (lstinline can't span lines)
        # Unescape lstinline escapes and re-escape for texttt
        clean = inner.replace("\\" + delim, delim)  # \! -> !
        clean = re.sub(r'(?<!\\)\\(?!\\)', '', clean)  # remove lone backslashes
        clean = clean.replace("_", r"\_").replace("&", r"\&")
        clean = clean.replace("%", r"\%").replace("#", r"\#")
        clean = clean.replace("{", r"\{").replace("}", r"\}")
        return r"\texttt{" + clean + "}"

    content = re.sub(
        r"\\passthrough\{\\lstinline(!)(.*?)\1\}",
        fix_multiline_lstinline,
        content,
        flags=re.DOTALL,
    )

    # --- Fix UTF-8 characters in lstinline/passthrough ---
    # Replace problematic Unicode inside \lstinline or \passthrough{\lstinline...}
    UNICODE_REPLACEMENTS = [
        ("\u2014", "---"),   # em-dash
        ("\u2013", "--"),    # en-dash
        ("\u2018", "'"),     # left single quote
        ("\u2019", "'"),     # right single quote
        ("\u201c", '"'),     # left double quote
        ("\u201d", '"'),     # right double quote
        ("\u2026", "..."),   # ellipsis
        ("\u00b1", "+/-"),   # plus-minus ±
        ("\u22a5", "bot"),   # ⊥
        ("\u22a4", "top"),   # ⊤
    ]
    for old_char, new_char in UNICODE_REPLACEMENTS:
        content = content.replace(old_char, new_char)

    # --- Fix escaping in texttt ---
    # Note: Do NOT unescape \_, \&, \%, \{, \} inside \texttt{} —
    # these are correct LaTeX escapes for special characters.

    # --- Fix unescaped underscores in \texttt{} inside section headings ---
    # Must run AFTER fix_texttt which unescapes \_ to _ everywhere
    def fix_texttt_underscore_in_heading(match):
        prefix = match.group(1)
        inner = match.group(2)
        suffix = match.group(3)
        fixed = re.sub(r'(?<!\\)_', r'\\_', inner)
        return prefix + fixed + suffix

    content = re.sub(
        r"(\\texorpdfstring\{[^}]*\\texttt\{)([^}]*?)(\}[^}]*\}\{[^}]*\})",
        fix_texttt_underscore_in_heading,
        content,
    )

    # --- Fix escaping in lstlisting ---
    LSTLISTING_UNICODE_FIXES = [
        ("\u2500", "-"),   # ─ box drawing horizontal
        ("\u2502", "|"),   # │ box drawing vertical
        ("\u250c", "+"),   # ┌
        ("\u2510", "+"),   # ┐
        ("\u2514", "+"),   # └
        ("\u2518", "+"),   # ┘
        ("\u251c", "+"),   # ├
        ("\u2524", "+"),   # ┤
        ("\u252c", "+"),   # ┬
        ("\u2534", "+"),   # ┴
        ("\u253c", "+"),   # ┼
        ("\u2550", "="),   # ═
        ("\u2551", "|"),   # ║
        ("\u25b6", ">"),   # ▶
        ("\u25ba", ">"),   # ►
        ("\u2192", "->"),  # →
        ("\u2190", "<-"),  # ←
        ("\u21d2", "=>"),  # ⇒
        ("\u2713", "[x]"), # ✓
        ("\u2717", "[ ]"), # ✗
        ("\u00d7", "x"),   # ×
        ("\u2264", "<="),  # ≤
        ("\u2265", ">="),  # ≥
        ("\u2260", "!="),  # ≠
        ("\u00b7", "."),   # ·
    ]

    def fix_lst(match):
        begin, body, end = match.group(1), match.group(2), match.group(3)
        for old, new in [(r"\{", "{"), (r"\}", "}"), (r"\&", "&"),
                         (r"\%", "%"), (r"\_", "_")]:
            body = body.replace(old, new)
        # Replace Unicode that pdflatex can't handle inside lstlisting
        for old_c, new_c in LSTLISTING_UNICODE_FIXES:
            body = body.replace(old_c, new_c)
        return begin + body + end

    content = re.sub(
        r"(\\begin\{lstlisting\}(?:\[[^\]]*\])?)(.*?)(\\end\{lstlisting\})",
        fix_lst,
        content,
        flags=re.DOTALL,
    )

    # --- Wrap standalone includegraphics in figure environments ---
    fig_counter = [0]

    def wrap_figure(match):
        fig_counter[0] += 1
        line = match.group(0)
        img_match = re.search(r"\\includegraphics(?:\[[^\]]*\])?\{([^}]+)\}", line)
        if not img_match or "res/" not in img_match.group(1):
            return line

        img_path = img_match.group(1)
        base_name = Path(img_path).stem
        label = f"fig:{chapter_id}-{base_name}"

        # Try to find caption from preceding heading (brace-aware)
        pos = match.start()
        preceding = content[:pos]
        caption = f"Diagram {fig_counter[0]}"
        for pat in [
            r"\\(?:sub)?(?:sub)?section\*?\{",
            r"\\paragraph\*?\{",
        ]:
            ms = list(re.finditer(pat, preceding))
            if ms:
                m = ms[-1]
                brace_start = m.end() - 1  # position of the opening {
                close = find_matching_brace(preceding, brace_start)
                if close != -1:
                    raw_caption = preceding[brace_start + 1 : close]
                    caption = sanitize_caption(raw_caption)
                break

        if img_path.endswith(".svg"):
            svg_no_ext = img_path[:-4]
            render = f"\\includesvg[width=0.95\\linewidth]{{{svg_no_ext}}}"
        else:
            render = line

        return (
            f"\\begin{{figure}}[htbp]\n"
            f"  \\centering\n"
            f"  {render}\n"
            f"  \\caption{{{caption}}}\n"
            f"  \\label{{{label}}}\n"
            f"\\end{{figure}}"
        )

    # Only wrap images not already inside figure environments
    parts = []
    i = 0
    while i < len(content):
        fig_start = content.find("\\begin{figure}", i)
        if fig_start == -1:
            parts.append(("normal", content[i:]))
            break
        if fig_start > i:
            parts.append(("normal", content[i:fig_start]))
        fig_end = content.find("\\end{figure}", fig_start)
        if fig_end == -1:
            parts.append(("normal", content[fig_start:]))
            break
        parts.append(("figure", content[fig_start : fig_end + len("\\end{figure}")]))
        i = fig_end + len("\\end{figure}")

    processed_parts = []
    for ptype, pcontent in parts:
        if ptype == "figure":
            processed_parts.append(pcontent)
        else:
            processed_parts.append(
                re.sub(
                    r"\\includegraphics(?:\[[^\]]*\])?\{res/[^}]+\}",
                    wrap_figure,
                    pcontent,
                )
            )
    content = "".join(processed_parts)

    # --- Remove leftover \textbackslash index{...} from markdown preprocessing ---
    content = re.sub(
        r"\\textbackslash\s*index\\\{([^}]*)\\\}",
        lambda m: f"\\index{{{m.group(1)}}}",
        content,
    )
    # Also clean up any that survived in a different encoding form
    content = re.sub(
        r"\\textbackslash\s*index\{([^}]*)\}",
        lambda m: f"\\index{{{m.group(1)}}}",
        content,
    )

    # --- Inject \index{} for section headings (brace-aware) ---
    def inject_index_all(content_text: str) -> str:
        """Find section headings with balanced braces and inject index entries."""
        result = []
        pos = 0
        sec_pat = re.compile(r"\\(?:sub)?(?:sub)?section\*?\{")
        while pos < len(content_text):
            m = sec_pat.search(content_text, pos)
            if not m:
                result.append(content_text[pos:])
                break
            result.append(content_text[pos:m.start()])
            brace_start = m.end() - 1  # position of the opening {
            close = find_matching_brace(content_text, brace_start)
            if close == -1:
                result.append(content_text[m.start():])
                break
            full_heading = content_text[m.start():close + 1]
            inner = content_text[brace_start + 1:close]
            # Create sanitized index text
            idx_text = sanitize_caption(inner)
            if idx_text:
                result.append(full_heading + f"\n\\index{{{idx_text}}}")
            else:
                result.append(full_heading)
            pos = close + 1
        return "".join(result)

    content = inject_index_all(content)

    # --- Convert \paragraph to \subsubsection* ---
    content = re.sub(
        r"\\paragraph\{([^}]+)\}(\\label\{[^}]+\})?",
        lambda m: f"\\subsubsection*{{{m.group(1)}}}{m.group(2) or ''}",
        content,
    )

    # --- Normalize lstlisting language/style options ---
    LANG_MAP = {
        "java": ("java", "Java"),
        "Java": ("java", "Java"),
        "c": ("c", "C"),
        "C": ("c", "C"),
        "assembly": ("frisc", "frisc"),
        "asm": ("frisc", "frisc"),
        "frisc": ("frisc", "frisc"),
        "pseudo": ("pseudocode", "pseudocode"),
        "pseudocode": ("pseudocode", "pseudocode"),
    }

    def normalize_lst(match):
        begin = match.group(1)
        opts = match.group(2) or ""
        body = match.group(3)
        end = match.group(4)

        opts_inner = opts.strip("[]")
        lang_match = re.search(r"language=\{?(\w+)\}?", opts_inner)

        if lang_match:
            lang_key = lang_match.group(1)
            if lang_key in LANG_MAP:
                style, lang = LANG_MAP[lang_key]
                opts_inner = re.sub(r"language=\{?\w+\}?", f"language={lang}", opts_inner)
                if "style=" not in opts_inner:
                    opts_inner = f"style={style}, {opts_inner}"
        else:
            # Auto-detect from content
            if any(kw in body for kw in ["MOVE", "LOAD", "STORE", "CALL", "RET", "PUSH", "HALT"]):
                if "style=" not in opts_inner:
                    opts_inner = f"style=frisc, language=frisc" + (f", {opts_inner}" if opts_inner else "")
            elif not opts_inner or "style=" not in opts_inner:
                opts_inner = f"style=generic" + (f", {opts_inner}" if opts_inner else "")

        new_opts = f"[{opts_inner}]" if opts_inner else ""
        return f"{begin}{new_opts}\n{body}{end}"

    content = re.sub(
        r"(\\begin\{lstlisting\})(\[.*?\])?\s*\n(.*?)(\\end\{lstlisting\})",
        normalize_lst,
        content,
        flags=re.DOTALL,
    )

    # --- Add captions/labels to listings ---
    lst_counter = [0]

    def add_lst_caption(match):
        lst_counter[0] += 1
        begin = match.group(1)
        opts = match.group(2) or ""
        body = match.group(3)
        end = match.group(4)

        if lst_counter[0] > 120:
            return match.group(0)

        # Skip if already has caption
        if "caption=" in opts:
            return match.group(0)

        # Find nearest heading for caption (brace-aware)
        pos = match.start()
        preceding = content[:pos]
        caption = f"Code listing {lst_counter[0]}"
        sec_pat = re.compile(r"\\(?:sub)?(?:sub)?section\*?\{")
        ms = list(sec_pat.finditer(preceding))
        if ms:
            m = ms[-1]
            brace_start = m.end() - 1
            close = find_matching_brace(preceding, brace_start)
            if close != -1:
                raw = preceding[brace_start + 1 : close]
                caption = sanitize_caption(raw)
                if not caption:
                    caption = f"Code listing {lst_counter[0]}"

        label = f"lst:{chapter_id}-{slugify(caption)}-{lst_counter[0]:02d}"

        if opts:
            new_opts = opts.rstrip("]") + f", caption={{{caption}}}, label={{{label}}}]"
        else:
            new_opts = f"[caption={{{caption}}}, label={{{label}}}]"

        return f"{begin}{new_opts}\n{body}{end}"

    content = re.sub(
        r"(\\begin\{lstlisting\})(\[[^\]]*\])?\n(.*?)(\\end\{lstlisting\})",
        add_lst_caption,
        content,
        flags=re.DOTALL,
    )

    # --- Ensure spacing between headings and listings ---
    content = re.sub(
        r"(\\subsubsection\*?\{[^}]+\}(?:\\label\{[^}]+\})?)\s*\n(\\begin\{lstlisting\})",
        r"\1\n\n\2",
        content,
    )

    if content != original:
        tex_file.write_text(content, encoding="utf-8")
        print(f"  [post] Enhanced: {tex_file.name}")


# ===========================================================================
# LaTeX class file
# ===========================================================================

def create_latex_class():
    """Write the frisc-compiler-book.cls class file."""
    cls_file = BOOK_DIR / "frisc-compiler-book.cls"
    cls_content = r"""\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{frisc-compiler-book}[2025/12/01 FRISC Compiler Book Class]

% Base class
\LoadClass[11pt,a4paper,twoside,openright]{book}

% ── Colors (must be before ANY package that loads xcolor) ────────────────
\PassOptionsToPackage{table,svgnames,dvipsnames}{xcolor}
\RequirePackage{xcolor}

% ── Packages ─────────────────────────────────────────────────────────────
\RequirePackage[T1]{fontenc}
\RequirePackage[utf8]{inputenc}
\RequirePackage{lmodern}
\RequirePackage{microtype}

% Layout
\RequirePackage{geometry}
\geometry{
  top=2.5cm,
  bottom=2.5cm,
  inner=3cm,
  outer=2.5cm,
  headheight=23pt,
  footskip=1.2cm,
}

% Graphics
\RequirePackage{graphicx}
\RequirePackage{adjustbox}
\RequirePackage{float}
\RequirePackage{svg}
\svgsetup{inkscape=inkscape}
\svgpath{{res/}}

% Math
\RequirePackage{amsmath,amssymb,amsthm}
\RequirePackage{stmaryrd}

% Tables
\RequirePackage{booktabs}
\RequirePackage{longtable}
\RequirePackage{tabularx}
\RequirePackage{multirow}
\RequirePackage{array}
\RequirePackage{calc}

% Hyperlinks
\RequirePackage[
  colorlinks=true,
  linkcolor=NavyBlue,
  citecolor=NavyBlue,
  urlcolor=NavyBlue,
  filecolor=NavyBlue,
  pdfborder={0 0 0},
  bookmarksnumbered=true,
  bookmarksopen=true,
  bookmarksopenlevel=1,
]{hyperref}

\RequirePackage{makeidx}
\makeindex

% Unicode
\RequirePackage{newunicodechar}
\RequirePackage{textcomp}
\RequirePackage{upquote}

% Headers/footers
\RequirePackage{fancyhdr}
\RequirePackage{titling}
\RequirePackage{titlesec}

% Code listings
\RequirePackage{listings}
\RequirePackage{listingsutf8}

% Algorithms
\RequirePackage[ruled,vlined,linesnumbered]{algorithm2e}

% Colored boxes
\RequirePackage[most]{tcolorbox}

% Fancy verbatim
\RequirePackage{fancyvrb}
\RequirePackage{fvextra}

% Epigraphs and quotes
\RequirePackage{epigraph}

% Misc
\RequirePackage{enumitem}
\RequirePackage{caption}
\RequirePackage{subcaption}
\RequirePackage{etoolbox}
\RequirePackage{xspace}

% Named colors
\definecolor{NavyBlue}{RGB}{0,51,102}

% ── Color Palette ────────────────────────────────────────────────────────
\definecolor{codebg}{RGB}{249,249,249}
\definecolor{codeframe}{RGB}{200,200,200}
\definecolor{codenumbg}{RGB}{240,240,240}
\definecolor{keyword}{RGB}{0,0,160}
\definecolor{string}{RGB}{163,21,21}
\definecolor{comment}{RGB}{0,128,0}
\definecolor{typecol}{RGB}{100,0,150}

% Box colors
\definecolor{defbox}{RGB}{230,240,255}
\definecolor{defborder}{RGB}{50,100,200}
\definecolor{thmbox}{RGB}{255,248,230}
\definecolor{thmborder}{RGB}{200,150,50}
\definecolor{notebox}{RGB}{235,250,235}
\definecolor{noteborder}{RGB}{60,140,60}
\definecolor{warnbox}{RGB}{255,238,238}
\definecolor{warnborder}{RGB}{200,50,50}
\definecolor{exambox}{RGB}{255,250,240}
\definecolor{examborder}{RGB}{190,140,80}
\definecolor{pipebox}{RGB}{240,240,255}
\definecolor{pipeborder}{RGB}{100,100,180}

% Chapter heading colors
\definecolor{chaptercolor}{RGB}{0,51,102}
\definecolor{sectioncolor}{RGB}{0,70,130}
\definecolor{chapternumcolor}{RGB}{180,200,225}

% Table alternate row
\definecolor{tablealt}{RGB}{245,248,252}

% ── Chapter and Section Styling ──────────────────────────────────────────
% Professional chapter opening: large number + decorative rule + title
\titleformat{\chapter}[display]
  {\normalfont\bfseries\color{chaptercolor}}
  {\filleft\fontsize{72}{72}\selectfont\color{chapternumcolor}\thechapter}
  {-10pt}
  {\titlerule[2pt]\vspace{6pt}\filright\fontsize{24}{28}\selectfont}
  [\vspace{8pt}{\titlerule[0.8pt]}]

\titlespacing*{\chapter}{0pt}{40pt}{30pt}

\titleformat{\section}
  {\normalfont\Large\bfseries\color{sectioncolor}}
  {\thesection}{1em}{}
\titleformat{\subsection}
  {\normalfont\large\bfseries}{\thesubsection}{1em}{}
\titleformat{\subsubsection}
  {\normalfont\normalsize\bfseries}{\thesubsubsection}{1em}{}

% ── Caption Styling ──────────────────────────────────────────────────────
\captionsetup{
  font={small},
  labelfont={bf,color=sectioncolor},
  labelsep=period,
  skip=8pt,
  format=hang,
}
\captionsetup[lstlisting]{
  font={small},
  labelfont={bf,color=sectioncolor},
  labelsep=period,
  skip=4pt,
}

% ── Image Handling ───────────────────────────────────────────────────────
\makeatletter
\setkeys{Gin}{width=0.85\textwidth,height=0.7\textheight,keepaspectratio}
\let\@oldincludegraphics\includegraphics
\renewcommand{\includegraphics}[2][]{%
  \begin{center}
    \IfFileExists{#2}{%
      \adjustbox{max width=0.85\textwidth,max height=0.7\textheight,keepaspectratio,center}{%
        \@oldincludegraphics[#1]{#2}}%
    }{%
      \fbox{\textcolor{red}{Missing: #2}}%
    }
  \end{center}
}
\makeatother

% Pandoc compatibility
\newcommand{\pandocbounded}[1]{#1}

\makeatletter
\def\Gin@extensions{.pdf,.png,.jpg,.jpeg,.svg,.mps,.eps}
\makeatother

% ── Unicode Characters ───────────────────────────────────────────────────
% Greek lowercase
\newunicodechar{α}{\ensuremath{\alpha}}
\newunicodechar{β}{\ensuremath{\beta}}
\newunicodechar{γ}{\ensuremath{\gamma}}
\newunicodechar{δ}{\ensuremath{\delta}}
\newunicodechar{ε}{\ensuremath{\varepsilon}}
\newunicodechar{ζ}{\ensuremath{\zeta}}
\newunicodechar{η}{\ensuremath{\eta}}
\newunicodechar{θ}{\ensuremath{\theta}}
\newunicodechar{ι}{\ensuremath{\iota}}
\newunicodechar{κ}{\ensuremath{\kappa}}
\newunicodechar{λ}{\ensuremath{\lambda}}
\newunicodechar{μ}{\ensuremath{\mu}}
\newunicodechar{ν}{\ensuremath{\nu}}
\newunicodechar{ξ}{\ensuremath{\xi}}
\newunicodechar{ο}{\ensuremath{o}}
\newunicodechar{π}{\ensuremath{\pi}}
\newunicodechar{ρ}{\ensuremath{\rho}}
\newunicodechar{σ}{\ensuremath{\sigma}}
\newunicodechar{τ}{\ensuremath{\tau}}
\newunicodechar{υ}{\ensuremath{\upsilon}}
\newunicodechar{φ}{\ensuremath{\phi}}
\newunicodechar{χ}{\ensuremath{\chi}}
\newunicodechar{ψ}{\ensuremath{\psi}}
\newunicodechar{ω}{\ensuremath{\omega}}

% Greek uppercase
\newunicodechar{Γ}{\ensuremath{\Gamma}}
\newunicodechar{Δ}{\ensuremath{\Delta}}
\newunicodechar{Θ}{\ensuremath{\Theta}}
\newunicodechar{Λ}{\ensuremath{\Lambda}}
\newunicodechar{Ξ}{\ensuremath{\Xi}}
\newunicodechar{Π}{\ensuremath{\Pi}}
\newunicodechar{Σ}{\ensuremath{\Sigma}}
\newunicodechar{Φ}{\ensuremath{\Phi}}
\newunicodechar{Ψ}{\ensuremath{\Psi}}
\newunicodechar{Ω}{\ensuremath{\Omega}}

% Arrows
\newunicodechar{→}{\ensuremath{\rightarrow}}
\newunicodechar{⇒}{\ensuremath{\Rightarrow}}
\newunicodechar{←}{\ensuremath{\leftarrow}}
\newunicodechar{⇐}{\ensuremath{\Leftarrow}}
\newunicodechar{↔}{\ensuremath{\leftrightarrow}}
\newunicodechar{⇔}{\ensuremath{\Leftrightarrow}}
\newunicodechar{↦}{\ensuremath{\mapsto}}
\newunicodechar{⟶}{\ensuremath{\longrightarrow}}
\newunicodechar{⟹}{\ensuremath{\Longrightarrow}}
\newunicodechar{↪}{\ensuremath{\hookrightarrow}}

% Math symbols
\newunicodechar{·}{\ensuremath{\cdot}}
\newunicodechar{×}{\ensuremath{\times}}
\newunicodechar{÷}{\ensuremath{\div}}
\newunicodechar{±}{\ensuremath{\pm}}
\newunicodechar{≤}{\ensuremath{\leq}}
\newunicodechar{≥}{\ensuremath{\geq}}
\newunicodechar{≠}{\ensuremath{\neq}}
\newunicodechar{≈}{\ensuremath{\approx}}
\newunicodechar{≡}{\ensuremath{\equiv}}
\newunicodechar{∈}{\ensuremath{\in}}
\newunicodechar{∉}{\ensuremath{\notin}}
\newunicodechar{⊂}{\ensuremath{\subset}}
\newunicodechar{⊆}{\ensuremath{\subseteq}}
\newunicodechar{∪}{\ensuremath{\cup}}
\newunicodechar{∩}{\ensuremath{\cap}}
\newunicodechar{∅}{\ensuremath{\emptyset}}
\newunicodechar{∞}{\ensuremath{\infty}}
\newunicodechar{∑}{\ensuremath{\sum}}
\newunicodechar{∏}{\ensuremath{\prod}}
\newunicodechar{∀}{\ensuremath{\forall}}
\newunicodechar{∃}{\ensuremath{\exists}}
\newunicodechar{∧}{\ensuremath{\land}}
\newunicodechar{∨}{\ensuremath{\lor}}
\newunicodechar{¬}{\ensuremath{\neg}}
\newunicodechar{⊕}{\ensuremath{\oplus}}
\newunicodechar{⊥}{\ensuremath{\perp}}
\newunicodechar{⊤}{\ensuremath{\top}}
\newunicodechar{⊢}{\ensuremath{\vdash}}
\newunicodechar{⊨}{\ensuremath{\models}}

% BNF symbols
\newunicodechar{⟨}{\ensuremath{\langle}}
\newunicodechar{⟩}{\ensuremath{\rangle}}

% Punctuation and misc
\newunicodechar{…}{\ensuremath{\ldots}}
\newunicodechar{⋯}{\ensuremath{\cdots}}
\newunicodechar{⋮}{\ensuremath{\vdots}}
\newunicodechar{′}{\ensuremath{^{\prime}}}
\newunicodechar{²}{\ensuremath{^2}}
\newunicodechar{³}{\ensuremath{^3}}
\newunicodechar{₀}{\ensuremath{_0}}
\newunicodechar{₁}{\ensuremath{_1}}
\newunicodechar{₂}{\ensuremath{_2}}
\newunicodechar{₃}{\ensuremath{_3}}

% Box-drawing
\newunicodechar{├}{\textSFii}
\newunicodechar{─}{\textSFx}
\newunicodechar{│}{\textSFxi}
\newunicodechar{└}{\textSFviii}
\newunicodechar{┐}{\textSFiii}
\newunicodechar{┌}{\textSFi}
\newunicodechar{┘}{\textSFvii}
\newunicodechar{┴}{\textSFvi}

% Formal languages
\newunicodechar{•}{\ensuremath{\bullet}}

% ── Code Listings Configuration ──────────────────────────────────────────
\lstset{
  inputencoding=utf8,
  extendedchars=true,
  basicstyle=\ttfamily\footnotesize,
  breaklines=true,
  breakatwhitespace=false,
  breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible,
  keepspaces=true,
  showspaces=false,
  showstringspaces=false,
  showtabs=false,
  tabsize=2,
  frame=single,
  frameround=tttt,
  rulecolor=\color{codeframe},
  backgroundcolor=\color{codebg},
  numberstyle=\tiny\color{gray},
  numbersep=5pt,
  xleftmargin=15pt,
  xrightmargin=5pt,
  framexleftmargin=10pt,
  framexrightmargin=5pt,
  numbers=left,
  captionpos=b,
  aboveskip=\medskipamount,
  belowskip=\medskipamount,
  literate=
    {α}{{\ensuremath{\alpha}}}1
    {β}{{\ensuremath{\beta}}}1
    {γ}{{\ensuremath{\gamma}}}1
    {δ}{{\ensuremath{\delta}}}1
    {ε}{{\ensuremath{\varepsilon}}}1
    {λ}{{\ensuremath{\lambda}}}1
    {μ}{{\ensuremath{\mu}}}1
    {π}{{\ensuremath{\pi}}}1
    {σ}{{\ensuremath{\sigma}}}1
    {φ}{{\ensuremath{\phi}}}1
    {Γ}{{\ensuremath{\Gamma}}}1
    {Δ}{{\ensuremath{\Delta}}}1
    {Σ}{{\ensuremath{\Sigma}}}1
    {→}{{$\rightarrow$}}1
    {⇒}{{$\Rightarrow$}}1
    {←}{{$\leftarrow$}}1
    {↦}{{$\mapsto$}}1
    {≤}{{$\leq$}}1
    {≥}{{$\geq$}}1
    {≠}{{$\neq$}}1
    {∈}{{$\in$}}1
    {∅}{{$\emptyset$}}1
    {∀}{{$\forall$}}1
    {∃}{{$\exists$}}1
    {∧}{{$\land$}}1
    {∨}{{$\lor$}}1
    {¬}{{$\neg$}}1
    {⟨}{{$\langle$}}1
    {⟩}{{$\rangle$}}1
    {…}{{$\ldots$}}1
    {•}{{$\bullet$}}1
    {š}{{\v{s}}}1
    {č}{{\v{c}}}1
    {ž}{{\v{z}}}1
    {ć}{{\'c}}1
    {đ}{{\dj{}}}1
    {Š}{{\v{S}}}1
    {Č}{{\v{C}}}1
    {Ž}{{\v{Z}}}1
    {Ć}{{\'C}}1
    {Đ}{{\DJ{}}}1
}

% Java style
\lstdefinestyle{java}{
  language=Java,
  basicstyle=\ttfamily\footnotesize,
  breaklines=true, breakatwhitespace=false, breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible, keepspaces=true,
  frame=single, frameround=tttt,
  rulecolor=\color{codeframe}, backgroundcolor=\color{codebg},
  keywordstyle=\color{keyword}\bfseries,
  commentstyle=\color{comment}\itshape,
  stringstyle=\color{string},
  numbers=left, numberstyle=\tiny\color{gray}, numbersep=5pt,
  xleftmargin=15pt, xrightmargin=5pt,
  framexleftmargin=10pt, framexrightmargin=5pt, captionpos=b,
}

% C style
\lstdefinestyle{c}{
  language=C,
  basicstyle=\ttfamily\footnotesize,
  breaklines=true, breakatwhitespace=false, breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible, keepspaces=true,
  frame=single, frameround=tttt,
  rulecolor=\color{codeframe}, backgroundcolor=\color{codebg},
  keywordstyle=\color{keyword}\bfseries,
  commentstyle=\color{comment}\itshape,
  stringstyle=\color{string},
  morekeywords={int,char,float,void,struct,const,return,if,else,while,for,break,continue,sizeof},
  numbers=left, numberstyle=\tiny\color{gray}, numbersep=5pt,
  xleftmargin=15pt, xrightmargin=5pt,
  framexleftmargin=10pt, framexrightmargin=5pt, captionpos=b,
}

% FRISC assembly style
\lstdefinelanguage{frisc}{
  keywords={ADD,ADC,SUB,SBC,CMP,AND,OR,XOR,SHL,SHR,ASHR,ROTL,ROTR,MOVE,LOAD,LOADB,LOADH,STORE,STOREB,STOREH,PUSH,POP,JP,CALL,RET,RETI,RETN,HALT,NOP,
    JP_Z,JP_NZ,JP_C,JP_NC,JP_V,JP_NV,JP_N,JP_NN,JP_M,JP_P,JP_EQ,JP_NE,
    JP_ULE,JP_UGT,JP_ULT,JP_UGE,JP_SLE,JP_SGT,JP_SLT,JP_SGE,
    CALL_Z,CALL_NZ,CALL_C,CALL_NC,
    RET_Z,RET_NZ,RET_C,RET_NC},
  comment=[l]{;},
  morecomment=[l]{//},
  sensitive=false,
}
\lstdefinestyle{frisc}{
  language=frisc,
  basicstyle=\ttfamily\small,
  breaklines=true, breakatwhitespace=false, breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible, keepspaces=true,
  frame=single, frameround=tttt,
  rulecolor=\color{codeframe}, backgroundcolor=\color{codebg},
  keywordstyle=\color{keyword}\bfseries,
  commentstyle=\color{comment}\itshape,
  numbers=left, numberstyle=\tiny\color{gray}, numbersep=5pt,
  xleftmargin=15pt, xrightmargin=5pt,
  framexleftmargin=10pt, framexrightmargin=5pt, captionpos=b,
}

% Pseudocode style
\lstdefinelanguage{pseudocode}{
  keywords={Algorithm,Input,Output,Step,If,Then,Else,End,For,While,Do,Return,Function,Procedure,Call},
  comment=[l]{//},
  sensitive=false,
}
\lstdefinestyle{pseudocode}{
  language=pseudocode,
  basicstyle=\ttfamily\small,
  breaklines=true, breakatwhitespace=false, breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible, keepspaces=true,
  frame=single, frameround=tttt,
  rulecolor=\color{codeframe}, backgroundcolor=\color{codebg},
  keywordstyle=\color{keyword}\bfseries,
  commentstyle=\color{comment}\itshape,
  numbers=left, numberstyle=\tiny\color{gray}, numbersep=5pt,
  xleftmargin=15pt, xrightmargin=5pt,
  framexleftmargin=10pt, framexrightmargin=5pt, captionpos=b,
}

% Generic style
\lstdefinestyle{generic}{
  basicstyle=\ttfamily\footnotesize,
  breaklines=true, breakatwhitespace=false, breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible, keepspaces=true,
  frame=single, frameround=tttt,
  rulecolor=\color{codeframe}, backgroundcolor=\color{codebg},
  numbers=left, numberstyle=\tiny\color{gray}, numbersep=5pt,
  xleftmargin=15pt, xrightmargin=5pt,
  framexleftmargin=10pt, framexrightmargin=5pt, captionpos=b,
}

% IR style
\lstdefinestyle{ir}{
  basicstyle=\ttfamily\footnotesize,
  breaklines=true, breakatwhitespace=false, breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible, keepspaces=true,
  frame=single, frameround=tttt,
  rulecolor=\color{codeframe}, backgroundcolor=\color{codebg},
  keywordstyle=\color{keyword}\bfseries,
  morekeywords={.program,.endprogram,.func,.endfunc,.frame,.slots,.blocks,.globals,.type,
    global,param,local,spill,
    add,sub,mul,div,mod,and,or,xor,shl,shr,
    cmp_eq,cmp_ne,cmp_lt,cmp_le,cmp_gt,cmp_ge,
    neg,not,preinc,postinc,predec,postdec,
    trunc,sext,zext,ptrcast,itof,ftoi,
    load,store,call,br,jmp,ret,
    addr_of_symbol,addr_index,addr_field,
    int32,char,uchar,float,bool,void,ptr,array,struct,null},
  numbers=left, numberstyle=\tiny\color{gray}, numbersep=5pt,
  xleftmargin=15pt, xrightmargin=5pt,
  framexleftmargin=10pt, framexrightmargin=5pt, captionpos=b,
}

\renewcommand{\lstlistingname}{Listing}
\renewcommand{\lstlistlistingname}{List of Code Listings}

% ── Semantic Environments (tcolorbox) ────────────────────────────────────
\newtcolorbox{definition}[1][]{
  colback=defbox, colframe=defborder,
  fonttitle=\bfseries, title=Definition,
  breakable, enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=defborder,colframe=defborder},
  #1
}
\newtcolorbox{theorem}[1][]{
  colback=thmbox, colframe=thmborder,
  fonttitle=\bfseries, title=Theorem,
  breakable, enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=thmborder,colframe=thmborder},
  #1
}
\newtcolorbox{notebox}[1][]{
  colback=notebox, colframe=noteborder,
  fonttitle=\bfseries, title=Note,
  breakable, enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=noteborder,colframe=noteborder},
  #1
}
\newtcolorbox{warning}[1][]{
  colback=warnbox, colframe=warnborder,
  fonttitle=\bfseries, title=Warning,
  breakable, enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=warnborder,colframe=warnborder},
  #1
}
\newtcolorbox{example}[1][]{
  colback=exambox, colframe=examborder,
  fonttitle=\bfseries, title=Example,
  breakable, enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=examborder,colframe=examborder},
  #1
}
\newtcolorbox{pipeline}[1][]{
  colback=pipebox, colframe=pipeborder,
  fonttitle=\bfseries, title=Pipeline Stage,
  breakable, enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=pipeborder,colframe=pipeborder},
  #1
}

% ── Pandoc Compatibility ─────────────────────────────────────────────────
\providecommand{\tightlist}{%
  \setlength{\itemsep}{0pt}\setlength{\parskip}{0pt}}

% Pandoc uses \passthrough for inline code when --listings is active
\providecommand{\passthrough}[1]{#1}

% Pandoc 3.x table column width: \real{0.5} expands to 0.5
\providecommand{\real}[1]{#1}

% Pandoc may use CSLReferences environment
\newlength{\cslhangindent}
\setlength{\cslhangindent}{1.5em}
\newlength{\csllabelwidth}
\setlength{\csllabelwidth}{3em}
\newenvironment{CSLReferences}[2]{}{\par}

% ── Formatting ───────────────────────────────────────────────────────────
\sloppy
\tolerance=2000
\emergencystretch=3em
\hfuzz=1pt

\makeatletter
\def\verbatim@font{\ttfamily\small}
\makeatother

\fvset{
  breaklines=true,
  breakanywhere=true,
  breakautoindent=true,
  baselinestretch=1.0,
  fontsize=\small,
}

\let\verbatim\Verbatim
\let\endverbatim\endVerbatim

% ── Custom Title Page ────────────────────────────────────────────────────
\renewcommand{\maketitle}{%
  \begin{titlepage}
    \centering
    \vspace*{2cm}
    % Decorative top rule
    {\color{chaptercolor}\rule{0.8\textwidth}{2pt}}
    \vspace{1.5cm}
    {\fontsize{32}{38}\selectfont\bfseries\color{chaptercolor} \thetitle\par}
    \vspace{1cm}
    \ifdefined\thesubtitle
      {\fontsize{16}{20}\selectfont\color{sectioncolor} \thesubtitle\par}
      \vspace{0.8cm}
    \fi
    {\color{chaptercolor}\rule{0.4\textwidth}{0.8pt}}
    \vspace{1.8cm}
    {\Large \theauthor\par}
    \vspace{0.6cm}
    {\large\itshape A Practical Guide to Building a C Compiler\par}
    {\large\itshape for the FRISC Processor Architecture\par}
    \vfill
    % Decorative bottom section
    {\color{chaptercolor}\rule{0.3\textwidth}{0.5pt}}
    \vspace{0.6cm}
    \ifx\thedate\empty
      {\large \today\par}
    \else
      {\large \thedate\par}
    \fi
    \ifdefined\theversion
      \vspace{0.3cm}
      {\normalsize Version \theversion\par}
    \fi
    \vspace{1cm}
  \end{titlepage}
  % Copyright page (verso of title)
  \thispagestyle{empty}
  \null\vfill
  \begin{flushleft}
    \textit{\thetitle}\\[4pt]
    \ifdefined\thesubtitle\textit{\thesubtitle}\\[4pt]\fi
    \theauthor\\[12pt]
    \textcopyright{} \the\year{} \theauthor. All rights reserved.\\[8pt]
    Faculty of Electrical Engineering and Computing\\
    University of Zagreb\\[12pt]
    \small
    This document was typeset using \LaTeX{}.\\
    Diagrams rendered with Mermaid and Inkscape.\\
    Code listings produced from actual compiler output.\\[8pt]
    \ifdefined\theversion Version \theversion\fi
  \end{flushleft}
  \cleardoublepage
}

\newcommand{\subtitle}[1]{\newcommand{\thesubtitle}{#1}}
\newcommand{\version}[1]{\newcommand{\theversion}{#1}}

% ── Lists of Figures / Tables / Listings ─────────────────────────────────
\makeatletter
\renewcommand{\@tocrmarg}{2.55em plus 1fil}
\makeatother

% ── Headers and Footers ──────────────────────────────────────────────────
\pagestyle{fancy}
\fancyhf{}
\fancyhead[LE]{\small\itshape\leftmark}
\fancyhead[RO]{\small\itshape\rightmark}
\fancyfoot[LE,RO]{\thepage}
\fancyfoot[RE,LO]{\small\itshape FRISCcc}
\renewcommand{\headrulewidth}{0.4pt}
\renewcommand{\footrulewidth}{0.2pt}

\fancypagestyle{plain}{%
  \fancyhf{}
  \fancyfoot[C]{\thepage}
  \renewcommand{\headrulewidth}{0pt}
  \renewcommand{\footrulewidth}{0pt}
}

% ── Table Row Striping ───────────────────────────────────────────────────
\newcommand{\stripedtable}{\rowcolors{2}{tablealt}{white}}

\endinput
"""
    cls_file.write_text(cls_content, encoding="utf-8")
    print(f"  [cls] Generated {cls_file}")


# ===========================================================================
# Pandoc runner
# ===========================================================================

def run_pandoc(input_md: Path, output_tex: Path):
    """Convert markdown to LaTeX fragment via Pandoc."""
    cmd = [
        PANDOC,
        "--from", "markdown+raw_tex+tex_math_single_backslash+lists_without_preceding_blankline",
        "--to", "latex",
        "--listings",
        "--wrap=none",
        "--preserve-tabs",
        "-o", str(output_tex),
        str(input_md),
    ]
    rc, stdout, stderr = run_cmd(cmd, timeout=60)
    if rc != 0:
        print(f"  [pandoc] WARNING: errors for {input_md.name}: {stderr[:200]}")
    else:
        print(f"  [pandoc] {output_tex.name}")

    # Post-process language styles
    post_process_lstlisting_styles(output_tex)


def sanitize_utf8(tex_file: Path):
    """Remove invalid UTF-8 sequences and fix problematic characters."""
    if not tex_file.exists():
        return
    raw = tex_file.read_bytes()
    clean = raw.decode("utf-8", errors="replace")
    # Remove replacement characters
    clean = clean.replace("\ufffd", "?")
    # Fix em-dash and en-dash inside lstlisting (pdflatex doesn't handle them in verbatim)
    # These cause "Invalid UTF-8 byte sequence" in older pdflatex

    # Replace problematic Unicode inside lstlisting blocks
    def fix_lst_unicode(match):
        begin = match.group(1)
        body = match.group(2)
        end = match.group(3)
        # Replace em-dash/en-dash with ASCII equivalents inside code blocks
        body = body.replace("\u2014", "---")  # em-dash
        body = body.replace("\u2013", "--")   # en-dash
        body = body.replace("\u2018", "'")    # left single quote
        body = body.replace("\u2019", "'")    # right single quote
        body = body.replace("\u201c", '"')    # left double quote
        body = body.replace("\u201d", '"')    # right double quote
        body = body.replace("\u2026", "...")   # ellipsis
        body = body.replace("\u00b1", "+/-")   # plus-minus ±
        body = body.replace("\u22a5", "bot")   # ⊥
        body = body.replace("\u22a4", "top")   # ⊤
        return begin + body + end

    clean = re.sub(
        r"(\\begin\{lstlisting\}(?:\[[^\]]*\])?)(.*?)(\\end\{lstlisting\})",
        fix_lst_unicode,
        clean,
        flags=re.DOTALL,
    )

    tex_file.write_text(clean, encoding="utf-8")


def post_process_lstlisting_styles(tex_file: Path):
    """Add style parameters for custom languages (frisc, pseudocode, etc.)."""
    if not tex_file.exists():
        return
    sanitize_utf8(tex_file)
    content = tex_file.read_text(encoding="utf-8")
    original = content

    replacements = [
        (r"\\begin\{lstlisting\}\[language=(frisc|asm)\]",
         r"\\begin{lstlisting}[language=frisc,style=frisc]"),
        (r"\\begin\{lstlisting\}\[language=\{(frisc|asm)\}\]",
         r"\\begin{lstlisting}[language=frisc,style=frisc]"),
        (r"\\begin\{lstlisting\}\[language=(pseudocode|pseudo)\]",
         r"\\begin{lstlisting}[language=pseudocode,style=pseudocode]"),
        (r"\\begin\{lstlisting\}\[language=\{(pseudocode|pseudo)\}\]",
         r"\\begin{lstlisting}[language=pseudocode,style=pseudocode]"),
        (r"\\begin\{lstlisting\}\[language=java\]",
         r"\\begin{lstlisting}[language=Java,style=java]"),
        (r"\\begin\{lstlisting\}\[language=\{java\}\]",
         r"\\begin{lstlisting}[language=Java,style=java]"),
        (r"\\begin\{lstlisting\}\[language=c\]",
         r"\\begin{lstlisting}[language=C,style=c]"),
        (r"\\begin\{lstlisting\}\[language=\{c\}\]",
         r"\\begin{lstlisting}[language=C,style=c]"),
        # IR blocks
        (r"\\begin\{lstlisting\}\[language=(ir|IR)\]",
         r"\\begin{lstlisting}[style=ir]"),
        (r"\\begin\{lstlisting\}\[language=\{(ir|IR)\}\]",
         r"\\begin{lstlisting}[style=ir]"),
    ]

    for pattern, replacement in replacements:
        content = re.sub(pattern, replacement, content)

    if content != original:
        tex_file.write_text(content, encoding="utf-8")


# ===========================================================================
# Generated content: compiler examples & benchmarks
# ===========================================================================

def generate_compiler_examples_tex(
    runner: CompilerRunner, chapter_id: str, examples: list
) -> str:
    """Generate LaTeX for compiler examples within a chapter."""
    if not examples:
        return ""

    parts = [
        r"\section{Compiler Output Examples}",
        r"\label{sec:" + chapter_id + r"-compiler-examples}",
        "",
        "The following examples demonstrate the compiler output for this phase.",
        "Each example shows the source program and the relevant intermediate artifacts.",
        "",
    ]

    for src_rel, desc in examples:
        src_path = PROJECT_ROOT / src_rel
        if not src_path.exists():
            print(f"  [examples] WARNING: {src_rel} not found, skipping")
            continue

        source_code = src_path.read_text(encoding="utf-8", errors="replace")

        # Determine which flags to use
        if "lexer" in chapter_id or "03_" in chapter_id:
            flags = ["--lex"]
            artifact = "tokens"
            art_label = "Token Stream"
        elif "parser" in chapter_id or "04_" in chapter_id:
            flags = ["--parse"]
            artifact = "ast"
            art_label = "Abstract Syntax Tree (excerpt)"
        elif "semantic" in chapter_id or "05_" in chapter_id:
            flags = ["--sem"]
            artifact = "ast"
            art_label = "Annotated Syntax Tree (excerpt)"
        elif "ir" in chapter_id or "06_" in chapter_id:
            flags = ["--O0", "--ir"]
            artifact = "ir"
            art_label = "Intermediate Representation"
        elif "optim" in chapter_id or "07_" in chapter_id:
            flags_o0 = ["--O0", "--ir"]
            flags_o1 = ["--O1", "--ir"]
            result_o0 = runner.compile(src_path, flags_o0)
            result_o1 = runner.compile(src_path, flags_o1)

            prog_name = src_path.parent.name.replace("_", r"\_")
            parts.append(f"\\subsection{{{desc} (\\texttt{{{prog_name}}})}}")
            parts.append("")
            parts.append(r"\begin{lstlisting}[style=c, caption={Source: " + desc + r"}]")
            parts.append(source_code.strip())
            parts.append(r"\end{lstlisting}")
            parts.append("")

            # Show O0 vs O1 IR comparison (first 40 lines each)
            ir_o0_lines = result_o0.get("ir", "").splitlines()[:40]
            ir_o1_lines = result_o1.get("ir", "").splitlines()[:40]

            parts.append(r"\begin{lstlisting}[style=ir, caption={IR at O0 (first 40 lines)}]")
            parts.append("\n".join(ir_o0_lines))
            parts.append(r"\end{lstlisting}")
            parts.append("")
            parts.append(r"\begin{lstlisting}[style=ir, caption={IR at O1 (first 40 lines)}]")
            parts.append("\n".join(ir_o1_lines))
            parts.append(r"\end{lstlisting}")
            parts.append("")
            parts.append(
                f"IR reduction: {result_o0.get('ir_lines', 0)} lines (O0) "
                f"$\\rightarrow$ {result_o1.get('ir_lines', 0)} lines (O1) = "
                f"{100 - round(result_o1.get('ir_lines', 1) / max(result_o0.get('ir_lines', 1), 1) * 100, 1)}\\% reduction."
            )
            parts.append("")
            continue
        elif "codegen" in chapter_id or "08_" in chapter_id:
            flags = ["--O1", "--all"]
            artifact = "frisc"
            art_label = "FRISC Assembly (excerpt)"
        elif "real_world" in chapter_id or "11_" in chapter_id:
            flags = ["--O1", "--all"]
            artifact = "frisc"
            art_label = "FRISC Assembly (excerpt)"
        else:
            flags = ["--O1", "--all"]
            artifact = "ir"
            art_label = "IR Output"

        result = runner.compile(src_path, flags)

        prog_name = src_path.parent.name.replace("_", r"\_")
        parts.append(f"\\subsection{{{desc} (\\texttt{{{prog_name}}})}}")
        parts.append("")

        # Source
        parts.append(r"\begin{lstlisting}[style=c, caption={Source: " + desc + r"}]")
        parts.append(source_code.strip())
        parts.append(r"\end{lstlisting}")
        parts.append("")

        # Artifact (truncated)
        art_content = result.get(artifact, "")
        art_lines = art_content.splitlines()
        max_lines = 60 if artifact in ("ir", "frisc") else 40
        if len(art_lines) > max_lines:
            shown = "\n".join(art_lines[:max_lines])
            shown += f"\n... ({len(art_lines) - max_lines} more lines)"
        else:
            shown = art_content.strip()

        if artifact == "ir":
            style = "ir"
        elif artifact == "frisc":
            style = "frisc"
        elif artifact == "tokens":
            style = "generic"
        else:
            style = "generic"

        parts.append(f"\\begin{{lstlisting}}[style={style}, caption={{{art_label}: {desc}}}]")
        parts.append(shown)
        parts.append(r"\end{lstlisting}")
        parts.append("")

    return "\n".join(parts)


def generate_benchmark_chapter_tex(runner: CompilerRunner) -> str:
    """Generate a full benchmark comparison appendix/section."""
    parts = [
        r"\section{Compilation Benchmarks}",
        r"\label{sec:benchmarks}",
        "",
        "This section presents quantitative measurements of the optimization pipeline.",
        "Each program is compiled at O0 (no optimization) and O1 (standard optimization),",
        "and the resulting IR and FRISC instruction counts are compared.",
        "",
        r"\stripedtable",
        r"\begin{longtable}{lrrrr}",
        r"\toprule",
        r"\textbf{Program} & \textbf{IR (O0)} & \textbf{IR (O1)} & \textbf{FRISC (O0)} & \textbf{FRISC (O1)} \\",
        r"\midrule",
        r"\endhead",
    ]

    totals = {"o0_ir": 0, "o1_ir": 0, "o0_frisc": 0, "o1_frisc": 0}

    for prog_rel in BENCHMARK_PROGRAMS:
        src = PROJECT_ROOT / prog_rel
        if not src.exists():
            continue
        bm = runner.benchmark(src)
        name = bm["program"].replace("_", r"\_")

        parts.append(
            f"  {name} & {bm['o0_ir_lines']} & {bm['o1_ir_lines']} "
            f"& {bm['o0_frisc_lines']} & {bm['o1_frisc_lines']} \\\\"
        )
        totals["o0_ir"] += bm["o0_ir_lines"]
        totals["o1_ir"] += bm["o1_ir_lines"]
        totals["o0_frisc"] += bm["o0_frisc_lines"]
        totals["o1_frisc"] += bm["o1_frisc_lines"]

    # Totals row
    parts.append(r"\midrule")
    parts.append(
        f"  \\textbf{{Total}} & {totals['o0_ir']} & {totals['o1_ir']} "
        f"& {totals['o0_frisc']} & {totals['o1_frisc']} \\\\"
    )

    # Reduction percentages
    ir_red = round((1 - totals["o1_ir"] / max(totals["o0_ir"], 1)) * 100, 1)
    frisc_red = round((1 - totals["o1_frisc"] / max(totals["o0_frisc"], 1)) * 100, 1)
    parts.append(
        f"  \\textbf{{Reduction}} & \\multicolumn{{2}}{{c}}{{{ir_red}\\%}} "
        f"& \\multicolumn{{2}}{{c}}{{{frisc_red}\\%}} \\\\"
    )

    parts.append(r"\bottomrule")
    parts.append(r"\caption{Compilation benchmark: O0 vs O1 instruction counts}")
    parts.append(r"\label{tab:benchmarks}")
    parts.append(r"\end{longtable}")
    parts.append("")

    return "\n".join(parts)


def generate_config_appendix_tex() -> str:
    """Generate LaTeX appendix with config file contents."""
    parts = []
    configs = [
        ("config/lexer_definition.txt", "Lexer Definition", "lexer-def"),
        ("config/parser_definition.txt", "Parser Definition (Grammar)", "parser-def"),
        ("config/semantics_definition.txt", "Semantic Rules", "semantics-def"),
        ("config/ir_definition.txt", "IR Grammar (BNF)", "ir-def"),
    ]

    for rel_path, title, label in configs:
        full_path = PROJECT_ROOT / rel_path
        if not full_path.exists():
            continue

        content = full_path.read_text(encoding="utf-8", errors="replace")
        # Replace problematic Unicode for lstlisting
        for old_c, new_c in [("\u2014", "---"), ("\u2013", "--"), ("\u2018", "'"),
                              ("\u2019", "'"), ("\u201c", '"'), ("\u201d", '"'),
                              ("\u2026", "..."), ("\u00b1", "+/-"),
                              ("\u22a5", "bot"), ("\u22a4", "top")]:
            content = content.replace(old_c, new_c)
        # Truncate very large files
        lines = content.splitlines()
        if len(lines) > 200:
            shown = "\n".join(lines[:200]) + f"\n... ({len(lines) - 200} more lines)"
        else:
            shown = content.strip()

        parts.append(f"\\section{{{title}}}")
        parts.append(f"\\label{{sec:config-{label}}}")
        parts.append("")
        parts.append(f"\\begin{{lstlisting}}[style=generic, caption={{{title}}}, label={{lst:config-{label}}}]")
        parts.append(shown)
        parts.append(r"\end{lstlisting}")
        parts.append("")

    return "\n".join(parts)


# ===========================================================================
# Glossary generator
# ===========================================================================

def generate_glossary_tex(docs_dir: Path) -> Optional[str]:
    """Extract bold term definitions from the glossary appendix file."""
    term_re = re.compile(r"^\*\*([^*]+)\*\*\s*$")
    terms = {}

    # Only parse the actual glossary file, not all docs
    glossary_file = docs_dir / "appendix" / "01_glossary.md"
    if not glossary_file.exists():
        # Fallback: search for any file with glossary in the name
        for candidate in sorted(docs_dir.rglob("*glossary*.md")):
            glossary_file = candidate
            break
        else:
            return None

    lines = glossary_file.read_text(encoding="utf-8", errors="replace").splitlines()
    i = 0
    while i < len(lines):
        m = term_re.match(lines[i].strip())
        if not m:
            i += 1
            continue
        term = m.group(1).strip()
        # Collect the full definition paragraph (may span multiple lines)
        definition_lines = []
        j = i + 1
        while j < len(lines):
            c = lines[j].strip()
            if c == "" and definition_lines:
                break  # blank line ends the definition
            if c == "---":
                break  # horizontal rule ends the definition
            if term_re.match(c):
                break  # next term starts
            if c:
                definition_lines.append(c)
            j += 1
        definition = " ".join(definition_lines)
        if term and definition and term not in terms:
            terms[term] = definition
        i = j

    if not terms:
        return None

    parts = [
        r"\section*{Glossary}",
        r"\addcontentsline{toc}{section}{Glossary}",
        "",
        r"\stripedtable",
        r"\begin{longtable}{p{4cm}p{10cm}}",
        r"\toprule",
        r"\textbf{Term} & \textbf{Definition} \\",
        r"\midrule",
        r"\endhead",
    ]

    def escape_texttt_inner(text):
        """Escape special LaTeX chars inside \\texttt{}."""
        text = text.replace("\\", "\\textbackslash{}")
        text = text.replace("{", "\\{")
        text = text.replace("}", "\\}")
        text = text.replace("_", "\\_")
        text = text.replace("&", "\\&")
        text = text.replace("#", "\\#")
        text = text.replace("%", "\\%")
        text = text.replace("$", "\\$")
        text = text.replace("^", "\\^{}")
        text = text.replace("~", "\\~{}")
        text = text.replace("<", "{\\textless}")
        text = text.replace(">", "{\\textgreater}")
        return text

    def md_to_latex_glossary(text):
        """Convert simple markdown formatting to LaTeX for glossary entries."""
        # Convert backtick code to \texttt with proper escaping
        def backtick_to_texttt(m):
            inner = escape_texttt_inner(m.group(1))
            return f"\\texttt{{{inner}}}"
        text = re.sub(r"`([^`]+)`", backtick_to_texttt, text)
        # Convert markdown italic *text* to \textit
        text = re.sub(r"\*([^*]+)\*", r"\\textit{\1}", text)
        # Escape remaining special chars
        for ch, esc in [("&", r"\&"), ("#", r"\#"), ("%", r"\%"), ("$", r"\$")]:
            # Only escape if not already escaped or inside a command
            text = re.sub(r'(?<!\\)' + re.escape(ch), esc, text)
        # Escape underscores outside of \texttt{} and \textit{}
        text = re.sub(r'(?<!\\)_(?![^{]*})', r'\\_', text)
        return text

    for term, defn in sorted(terms.items(), key=lambda x: x[0].lower()):
        safe_term = term.replace("&", r"\&").replace("_", r"\_").replace("#", r"\#").replace("%", r"\%")
        safe_def = md_to_latex_glossary(defn)
        if len(safe_def) > 400:
            safe_def = safe_def[:400] + "..."
        parts.append(f"  {safe_term} & {safe_def} \\\\")

    parts.append(r"\bottomrule")
    parts.append(r"\end{longtable}")
    return "\n".join(parts)


# ===========================================================================
# Main: main.tex assembly
# ===========================================================================

def create_main_tex(chapter_entries, extra_tex_files=None):
    """Generate the master main.tex file."""
    main_tex = BOOK_DIR / "main.tex"

    frontmatter = []
    mainmatter = []
    for entry in chapter_entries:
        if entry[0] == "00_frontmatter":
            frontmatter.append(entry)
        else:
            mainmatter.append(entry)

    lines = [
        r"\documentclass{frisc-compiler-book}",
        "",
        r"\title{Building a C Compiler for FRISC}",
        r"\subtitle{FRISCcc --- From Source to Machine Code}",
        r"\author{Karlo Kne\v{z}evi\'{c}}",
        r"\date{\today}",
        r"\version{1.0}",
        "",
        r"\begin{document}",
        r"\frontmatter",
        r"\maketitle",
        "",
    ]

    # Frontmatter
    for dir_name, _, tex_path in frontmatter:
        lines.append(f"% Frontmatter: {dir_name}")
        lines.append(r"\input{" + tex_path.replace("\\", "/") + "}")
        lines.append("")

    # Tables of contents
    lines.extend([
        r"\tableofcontents",
        r"\listoffigures",
        r"\listoftables",
        r"\lstlistoflistings",
        "",
    ])

    # Main matter
    lines.append(r"\mainmatter")
    lines.append("")

    for dir_name, chapter_title, tex_path in mainmatter:
        if dir_name == "appendix":
            lines.append(r"\appendix")
            lines.append("")
        lines.append(f"% Chapter: {dir_name}")
        lines.append(r"\chapter{" + chapter_title + "}")
        lines.append(r"\input{" + tex_path.replace("\\", "/") + "}")
        lines.append("")

    # Extra generated tex files (benchmarks, config appendices)
    if extra_tex_files:
        for label, tex_path in extra_tex_files:
            lines.append(f"% Generated: {label}")
            lines.append(r"\input{" + tex_path.replace("\\", "/") + "}")
            lines.append("")

    # Back matter
    lines.extend([
        r"\backmatter",
        r"\chapter*{Index}",
        r"\addcontentsline{toc}{chapter}{Index}",
        r"\printindex",
        "",
        r"\end{document}",
        "",
    ])

    main_tex.write_text("\n".join(lines), encoding="utf-8")
    print(f"  [main.tex] Generated")


# ===========================================================================
# LaTeX compilation
# ===========================================================================

def compile_latex():
    """Compile main.tex to PDF with multiple passes."""
    cwd = os.getcwd()
    try:
        os.chdir(BOOK_DIR)
        if not Path("main.tex").exists():
            print("ERROR: No main.tex found.")
            return False

        passes = [
            ("Pass 1/4: Initial compilation", True),
            ("Pass 2/4: Resolving references", False),
            ("Pass 3/4: Index generation", False),
            ("Pass 4/4: Final compilation", True),
        ]

        for i, (desc, verbose) in enumerate(passes):
            print(f"  [pdflatex] {desc}...")

            # Run makeindex after pass 2
            if i == 2 and Path("main.idx").exists():
                print(f"  [makeindex] Generating index...")
                run_cmd([MAKEINDEX, "main.idx"], timeout=30, cwd=str(BOOK_DIR))

            rc, stdout, stderr = run_cmd(
                [PDFLATEX, "-shell-escape", "-interaction=nonstopmode", "main.tex"],
                timeout=300,
                cwd=str(BOOK_DIR),
            )

            if rc != 0 and verbose:
                print(f"  [pdflatex] WARNING: return code {rc}")
                if not Path("main.pdf").exists():
                    print("  ERROR: PDF not generated. Check main.log")
                    if stderr:
                        print(f"  stderr: {stderr[:500]}")
                    return False

        if Path("main.pdf").exists():
            pdf_size = Path("main.pdf").stat().st_size
            try:
                log = Path("main.log").read_text(encoding="utf-8", errors="ignore")
                m = re.search(r"Output written on main\.pdf \((\d+) pages", log)
                pages = m.group(1) if m else "?"
                print(f"\n  PDF generated: main.pdf ({pages} pages, {pdf_size:,} bytes)")
            except Exception:
                print(f"\n  PDF generated: main.pdf ({pdf_size:,} bytes)")
            return True
        else:
            print("  ERROR: PDF not found after compilation.")
            return False

    finally:
        os.chdir(cwd)


# ===========================================================================
# Main pipeline
# ===========================================================================

def main():
    parser = argparse.ArgumentParser(description="Generate FRISCcc compiler book")
    parser.add_argument("--tex", action="store_true", help="Generate .tex only (skip PDF)")
    parser.add_argument("--quick", action="store_true", help="Skip compiler examples")
    parser.add_argument("--no-clean", action="store_true", help="Keep existing book/ dir")
    args = parser.parse_args()

    print("=" * 70)
    print("  FRISCcc Book Generator")
    print("=" * 70)
    start_time = time.time()

    # Validate environment
    if not DOCS_DIR.exists():
        print(f"ERROR: {DOCS_DIR} not found. Run from project root.")
        return 1

    # Clean and create book structure
    if not args.no_clean and BOOK_DIR.exists():
        # Preserve cache
        cache_backup = None
        if CACHE_DIR.exists():
            cache_backup = PROJECT_ROOT / ".book_cache_backup"
            if cache_backup.exists():
                shutil.rmtree(cache_backup)
            shutil.copytree(CACHE_DIR, cache_backup)

        print(f"\n[1/6] Cleaning book/ directory...")
        shutil.rmtree(BOOK_DIR)

        # Restore cache
        if cache_backup and cache_backup.exists():
            CACHE_DIR.mkdir(parents=True, exist_ok=True)
            for item in cache_backup.iterdir():
                dest = CACHE_DIR / item.name
                if item.is_dir():
                    shutil.copytree(item, dest)
                else:
                    shutil.copy2(item, dest)
            shutil.rmtree(cache_backup)

    CHAPTERS_DIR.mkdir(parents=True, exist_ok=True)
    RES_DIR.mkdir(parents=True, exist_ok=True)
    CACHE_DIR.mkdir(parents=True, exist_ok=True)

    # Generate class file
    print(f"\n[2/6] Generating LaTeX class...")
    create_latex_class()

    # Initialize compiler runner
    runner = None
    if not args.quick and COMPILER_JAR.exists():
        runner = CompilerRunner(COMPILER_JAR, CACHE_DIR)
        print(f"  Compiler JAR found: {COMPILER_JAR}")
    elif not args.quick:
        print(f"  WARNING: Compiler JAR not found at {COMPILER_JAR}")
        print(f"           Run ./build.sh first. Skipping examples.")

    # Build label mapping
    label_mapping = build_md_label_mapping(DOCS_DIR)
    print(f"  Built {len(label_mapping)} cross-reference mappings")

    # Process chapters
    print(f"\n[3/6] Processing documentation chapters...")
    chapter_entries = []
    extra_tex_files = []

    # Determine chapter dirs
    chapter_dirs = []
    for name in CHAPTER_ORDER:
        p = DOCS_DIR / name
        if p.exists() and p.is_dir():
            chapter_dirs.append(p)

    # Add any extra dirs not in CHAPTER_ORDER
    seen = {d.name for d in chapter_dirs}
    for d in sorted(DOCS_DIR.iterdir()):
        if d.is_dir() and d.name not in seen and not d.name.startswith("."):
            chapter_dirs.append(d)

    for chapter_dir in chapter_dirs:
        dir_name = chapter_dir.name
        title = CHAPTER_TITLES.get(dir_name)
        if title is None and dir_name != "00_frontmatter":
            # Generate title from dir name
            title_part = re.sub(r"^\d+[-_]?", "", dir_name)
            title = title_part.replace("-", " ").replace("_", " ").strip().title()
            if not title:
                title = dir_name

        print(f"\n  --- {dir_name} ---")

        # Collect .md files (excluding supplementary 96-101 files and large atlases)
        md_files = sorted(chapter_dir.glob("*.md"))
        md_files = [
            f for f in md_files
            if not any(f.name.startswith(p) for p in EXCLUDED_FILE_PREFIXES)
            and f.name not in EXCLUDED_FILES
        ]

        if not md_files:
            print(f"    No content files found, skipping")
            continue

        # Merge all .md into single chapter
        all_texts = []
        for md_file in md_files:
            raw = md_file.read_text(encoding="utf-8", errors="replace")
            prefix = f"{dir_name}_{md_file.stem}"

            # Strip filler content (Reinforcement Units, etc.)
            raw = strip_filler_content(raw)

            # Process Mermaid diagrams
            processed, figures = extract_and_replace_mermaid(raw, prefix, RES_DIR)

            # Preprocess markdown
            chapter_id = f"{dir_name}-{md_file.stem}"
            processed = preprocess_markdown(processed, label_mapping, chapter_id)

            all_texts.append(processed)

            if figures:
                rendered_count = sum(1 for f in figures if f["rendered"])
                print(f"    {md_file.name}: {len(figures)} diagrams ({rendered_count} rendered)")

        # Write merged markdown (sanitize UTF-8)
        chapter_md = CHAPTERS_DIR / f"{dir_name}.md"
        merged_text = "\n\n".join(all_texts) + "\n"
        # Remove any non-UTF-8 safe characters
        merged_text = merged_text.encode("utf-8", errors="replace").decode("utf-8", errors="replace")
        merged_text = merged_text.replace("\ufffd", "?")
        chapter_md.write_text(merged_text, encoding="utf-8")

        # Convert to LaTeX
        chapter_tex = CHAPTERS_DIR / f"{dir_name}.tex"
        run_pandoc(chapter_md, chapter_tex)

        # Post-process LaTeX
        post_process_latex(chapter_tex, dir_name)

        # Add compiler examples for relevant chapters
        if runner and dir_name in CHAPTER_EXAMPLES:
            examples = CHAPTER_EXAMPLES[dir_name]
            examples_tex = generate_compiler_examples_tex(runner, dir_name, examples)
            if examples_tex:
                # Append to chapter tex
                existing = chapter_tex.read_text(encoding="utf-8")
                chapter_tex.write_text(
                    existing + "\n\n" + examples_tex,
                    encoding="utf-8"
                )
                print(f"    Added {len(examples)} compiler examples")

        # Register chapter
        rel_tex = str(CHAPTERS_DIR.relative_to(BOOK_DIR) / chapter_tex.name)
        if dir_name == "00_frontmatter":
            chapter_entries.append((dir_name, None, rel_tex))
        else:
            chapter_entries.append((dir_name, title, rel_tex))

    # Generate benchmarks
    print(f"\n[4/6] Generating benchmarks and appendices...")
    if runner:
        print("  Running benchmark compilations...")
        benchmark_tex = generate_benchmark_chapter_tex(runner)
        bm_file = CHAPTERS_DIR / "_benchmarks.tex"
        bm_file.write_text(benchmark_tex, encoding="utf-8")
        extra_tex_files.append(
            ("Benchmarks", str(CHAPTERS_DIR.relative_to(BOOK_DIR) / bm_file.name))
        )
        print(f"    Benchmark table generated")

    # Config appendices
    config_tex = generate_config_appendix_tex()
    if config_tex:
        cfg_file = CHAPTERS_DIR / "_config_appendix.tex"
        cfg_file.write_text(config_tex, encoding="utf-8")
        extra_tex_files.append(
            ("Config Reference", str(CHAPTERS_DIR.relative_to(BOOK_DIR) / cfg_file.name))
        )
        print(f"    Config appendix generated")

    # Glossary
    glossary_tex = generate_glossary_tex(DOCS_DIR)
    if glossary_tex:
        gloss_file = CHAPTERS_DIR / "_glossary.tex"
        gloss_file.write_text(glossary_tex, encoding="utf-8")
        extra_tex_files.append(
            ("Glossary", str(CHAPTERS_DIR.relative_to(BOOK_DIR) / gloss_file.name))
        )
        print(f"    Glossary generated")

    # Generate main.tex
    print(f"\n[5/6] Assembling main.tex...")
    create_main_tex(chapter_entries, extra_tex_files)

    # Compile to PDF
    if not args.tex:
        print(f"\n[6/6] Compiling LaTeX to PDF...")
        if not Path(PDFLATEX).exists() and shutil.which("pdflatex") is None:
            print("  WARNING: pdflatex not found. Skipping PDF compilation.")
            print("  Install LaTeX: brew install --cask mactex")
            print("  Then run: cd book && pdflatex -shell-escape main.tex")
        else:
            compile_latex()
    else:
        print(f"\n[6/6] Skipping PDF compilation (--tex mode)")

    elapsed = time.time() - start_time
    print(f"\n{'=' * 70}")
    print(f"  Done in {elapsed:.1f}s")
    print(f"  Output: book/main.tex" + (" + book/main.pdf" if not args.tex else ""))
    print(f"{'=' * 70}")
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
