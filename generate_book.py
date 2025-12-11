#!/usr/bin/env python3
import os
import re
import shutil
import subprocess
from pathlib import Path

# Konstante
DOCS_DIR = Path("docs")
BOOK_DIR = Path("book")
CHAPTERS_DIR = BOOK_DIR / "chapters"
RES_DIR = BOOK_DIR / "res"

MERMAID_BLOCK_RE = re.compile(
    r"```mermaid\s+([\s\S]*?)```",
    re.MULTILINE
)

CODE_BLOCK_RE = re.compile(
    r"```(\w+)?\s*\n(.*?)```",
    re.DOTALL | re.MULTILINE
)

HEADING_RE = re.compile(
    r"^(#{1,6})\s+(.+)$",
    re.MULTILINE
)

MARKDOWN_LINK_RE = re.compile(
    r"\[([^\]]+)\]\(([^)]+\.md(?:#[^)]+)?)\)",
    re.MULTILINE
)

def slugify(text: str) -> str:
    text = text.strip()
    text = text.replace(" ", "-")
    text = text.replace("\t", "-")
    # Zadrži samo slova, brojke, crticu i podvlaku
    return re.sub(r"[^0-9A-Za-z_\-]+", "-", text).strip("-").lower()

def sanitize_mermaid_code(code: str) -> str:
    """
    Sanitizes Mermaid code to fix common parsing issues with mmdc.
    - Replaces HTML tags like <br/> with spaces
    - Replaces problematic characters in node labels only (not in graph syntax)
    - Fixes special characters that cause parsing errors
    """
    # Replace <br/> and <br> with spaces (Mermaid doesn't support HTML in labels)
    code = re.sub(r'<br\s*/?>', ' ', code, flags=re.IGNORECASE)
    
    # Replace HTML entities
    code = code.replace('&nbsp;', ' ')
    code = code.replace('&lt;', '<')
    code = code.replace('&gt;', '>')
    code = code.replace('&amp;', '&')
    
    # Process line by line to fix labels without breaking graph syntax
    lines = code.split('\n')
    sanitized_lines = []
    
    for line in lines:
        stripped = line.strip()
        
        # Don't modify graph/flowchart declarations or style lines
        if (stripped.startswith('graph') or 
            stripped.startswith('flowchart') or 
            stripped.startswith('style')):
            sanitized_lines.append(line)
            continue
        
        # Fix labels in square brackets by replacing problematic characters
        # Need to handle nested brackets (e.g., data[10] inside label)
        def fix_label_content(label: str) -> str:
            """Fix the content of a label."""
            # If label already has quotes, unquote first
            if label.startswith('"') and label.endswith('"'):
                label = label[1:-1].replace('\\"', '"')
            
            # Replace problematic characters in labels only
            label = label.replace('~', 'approx ')  # Replace ~ with text (e.g., "~823" -> "approx 823")
            label = label.replace('→', '->')       # Replace arrow with text (e.g., "x→int" -> "x->int")
            # Only replace * when it's clearly multiplication (has spaces around it)
            label = re.sub(r'\s+\*\s+', ' x ', label)  # "10 * 4" -> "10 x 4"
            # Ensure proper spacing around =
            label = re.sub(r'\s*=\s*', ' = ', label)  # "x=5" -> "x = 5"
            
            # Clean up extra spaces
            label = ' '.join(label.split())
            
            # Escape any existing quotes
            label = label.replace('"', '\\"')
            return label
        
        # Match node labels: pattern is identifier[label] or --> identifier[label]
        # Need to handle nested brackets properly (e.g., data[10] inside label)
        def fix_node_labels(text: str) -> str:
            """Fix all node labels in a line, handling nested brackets."""
            result = []
            i = 0
            while i < len(text):
                # Look for pattern: identifier followed by [
                # Match: word chars (node identifier), optional arrow, then [
                match = re.search(r'([A-Za-z_][A-Za-z0-9_]*)\s*(?:--?>)?\s*\[', text[i:])
                if match:
                    # Copy everything before the match
                    result.append(text[i:i+match.start()])
                    # Get the matched prefix (node_id + optional arrow + spaces, but not the [)
                    node_id = match.group(1)
                    full_match = match.group(0)
                    # Extract everything before the final [
                    bracket_idx = full_match.rfind('[')
                    prefix = full_match[:bracket_idx]  # Everything before the [
                    result.append(prefix)
                    # Calculate absolute position: i (start of search) + match.start() (relative) + bracket_idx + 1 (after [)
                    i = i + match.start() + bracket_idx + 1  # Position after the [
                    
                    # Find balanced brackets starting from current position
                    bracket_count = 1
                    label_start = i
                    while i < len(text) and bracket_count > 0:
                        if text[i] == '[':
                            bracket_count += 1
                        elif text[i] == ']':
                            bracket_count -= 1
                            if bracket_count == 0:
                                # Found the matching closing bracket
                                break
                        i += 1
                    
                    if bracket_count == 0:
                        # Found matching bracket - extract and fix label content
                        label = text[label_start:i]  # Content between [ and ]
                        fixed_label = fix_label_content(label)
                        result.append('[')
                        result.append(f'"{fixed_label}"')
                        result.append(']')
                        i += 1  # Move past the closing ]
                    else:
                        # Unmatched bracket, copy as-is
                        result.append('[')
                        result.append(text[label_start:i])
                else:
                    # No more matches, copy rest
                    result.append(text[i:])
                    break
            return ''.join(result) if result else text
        
        line = fix_node_labels(line)
        sanitized_lines.append(line)
    
    return '\n'.join(sanitized_lines)

def extract_and_replace_mermaid(markdown: str, prefix: str) -> tuple[str, list]:
    """
    Nađe sve ```mermaid``` blokove u markdown tekstu,
    svaki spremi kao .mmd + .pdf u RES_DIR i zamijeni blok
    s ![](res/<basename>.pdf).
    
    Returns:
        tuple: (processed_markdown, list of figure_info dicts)
        figure_info contains: {'base_name': str, 'pdf_path': Path, 'caption': str}
    """
    figure_info_list = []

    counter = 1

    def repl(match: re.Match) -> str:
        nonlocal counter
        mermaid_code = match.group(1).strip() + "\n"
        
        # Sanitize Mermaid code to fix parsing issues
        mermaid_code = sanitize_mermaid_code(mermaid_code)
        
        base_name = f"{prefix}_diag{counter:03d}"
        counter += 1

        mmd_path = RES_DIR / f"{base_name}.mmd"
        raw_pdf_path = RES_DIR / f"{base_name}_raw.pdf"
        pdf_path = RES_DIR / f"{base_name}.pdf"

        # Spremi sanitizirani mermaid kod
        mmd_path.write_text(mermaid_code, encoding="utf-8")

        # Step 1: Generate raw PDF using mmdc
        mmdc_success = False
        try:
            result = subprocess.run(
                [
                    "mmdc",
                    "-i", str(mmd_path),
                    "-o", str(raw_pdf_path),
                    "--pdfFit",  # Tell mermaid-cli to fit content
                    "-b", "transparent",  # Transparent background
                ],
                check=True,
                capture_output=True,
                text=True,
                timeout=30  # 30 second timeout
            )
            if raw_pdf_path.exists() and raw_pdf_path.stat().st_size > 0:
                print(f"[mmdc] Generated raw PDF: {raw_pdf_path}")
                mmdc_success = True
            else:
                print(f"WARNING: mmdc did not create valid {raw_pdf_path}")
        except FileNotFoundError:
            print("WARNING: 'mmdc' (mermaid-cli) not found. Mermaid diagrams will not be converted.")
            print("         Install with: npm install -g @mermaid-js/mermaid-cli")
            print("         LaTeX will use draft mode for missing images.")
        except subprocess.TimeoutExpired:
            print(f"WARNING: mmdc timed out for {mmd_path}")
        except subprocess.CalledProcessError as e:
            print(f"WARNING: mmdc failed for {mmd_path}: {e}")
            if e.stderr:
                print(f"         Error output: {e.stderr[:200]}")

        # Step 2: Crop the PDF to tight bounding box using pdfcrop
        if mmdc_success and raw_pdf_path.exists():
            try:
                subprocess.run(
                    [
                        "pdfcrop",
                        "--margins", "0",  # Remove all extra padding
                        str(raw_pdf_path),
                        str(pdf_path),
                    ],
                    check=True,
                    capture_output=True,
                    text=True,
                    timeout=30
                )
                if pdf_path.exists() and pdf_path.stat().st_size > 0:
                    print(f"[pdfcrop] Cropped PDF to tight bounding box: {pdf_path}")
                    # Delete the raw PDF to keep the folder clean
                    try:
                        raw_pdf_path.unlink()
                        print(f"[cleanup] Removed temporary raw PDF: {raw_pdf_path}")
                    except OSError as e:
                        print(f"WARNING: Could not delete raw PDF {raw_pdf_path}: {e}")
                else:
                    print(f"WARNING: pdfcrop did not create valid {pdf_path}, using raw PDF")
                    # Fallback: use raw PDF if crop failed
                    if raw_pdf_path.exists():
                        shutil.copy2(raw_pdf_path, pdf_path)
            except FileNotFoundError:
                print("WARNING: 'pdfcrop' not found. Using un-cropped PDF.")
                print("         Install pdfcrop (usually part of TeX Live):")
                print("         - macOS: brew install --cask mactex")
                print("         - Linux: sudo apt-get install texlive-extra-utils")
                # Fallback: use raw PDF if pdfcrop is not available
                if raw_pdf_path.exists():
                    shutil.copy2(raw_pdf_path, pdf_path)
                    print(f"[fallback] Using un-cropped PDF: {pdf_path}")
            except subprocess.TimeoutExpired:
                print(f"WARNING: pdfcrop timed out for {raw_pdf_path}")
                # Fallback: use raw PDF
                if raw_pdf_path.exists():
                    shutil.copy2(raw_pdf_path, pdf_path)
            except subprocess.CalledProcessError as e:
                print(f"WARNING: pdfcrop failed for {raw_pdf_path}: {e}")
                if e.stderr:
                    print(f"         Error output: {e.stderr[:200]}")
                # Fallback: use raw PDF
                if raw_pdf_path.exists():
                    shutil.copy2(raw_pdf_path, pdf_path)
                    print(f"[fallback] Using un-cropped PDF: {pdf_path}")

        # U markdownu zamijeni blok s linkom na PDF
        # Pandoc će ovo pretvoriti u \includegraphics, koji će biti automatski centriran i skaliran
        pdf_rel_path = Path('res') / (base_name + '.pdf')
        
        # Store figure info for later processing
        figure_info_list.append({
            'base_name': base_name,
            'pdf_path': pdf_rel_path,
            'caption': None  # Will be filled in post-processing
        })
        
        return f"![]({pdf_rel_path})"

    processed_markdown = MERMAID_BLOCK_RE.sub(repl, markdown)
    return processed_markdown, figure_info_list

def build_md_file_label_mapping(docs_dir: Path) -> dict:
    """
    Builds a mapping from markdown file paths (relative to docs/) to LaTeX labels.
    Returns: dict mapping relative_path -> label_id
    Example: "../07-code-generation/target-architecture-overview.md" -> "sec:07-code-generation-target-architecture-overview"
    """
    mapping = {}
    
    for chapter_dir in sorted(docs_dir.iterdir()):
        if not chapter_dir.is_dir():
            continue
        
        dir_name = chapter_dir.name  # e.g., "07-code-generation"
        
        for md_file in sorted(chapter_dir.glob("*.md")):
            # Create relative path from docs root
            rel_path = md_file.relative_to(docs_dir)
            rel_path_str = str(rel_path).replace("\\", "/")
            
            # Generate label ID: sec:07-code-generation-target-architecture-overview
            label_id = f"sec:{dir_name}-{md_file.stem}"
            mapping[rel_path_str] = label_id
            
            # Also map with "../" prefix for cross-chapter references
            mapping[f"../{rel_path_str}"] = label_id
            mapping[f"./{rel_path_str}"] = label_id
    
    return mapping

def preprocess_markdown(markdown: str, md_file_label_mapping: dict, chapter_id: str) -> str:
    """
    Preprocesses markdown before sending to Pandoc:
    1. Maps assembly/asm to frisc language
    2. Ensures bullet lists have blank lines before them
    3. Converts internal .md file links to cross-references
    4. Adds labels to headings
    """
    content = markdown
    
    # 1. Normalize code block language fences to match .cls expectations
    # Map assembly/asm to frisc
    content = re.sub(
        r"```(assembly|asm)\s*\n",
        r"```frisc\n",
        content,
        flags=re.MULTILINE
    )
    
    # Normalize pseudo to pseudocode
    content = re.sub(
        r"```pseudo\s*\n",
        r"```pseudocode\n",
        content,
        flags=re.MULTILINE
    )
    
    # 2. Ensure bullet lists have blank lines before them
    # Pattern: non-blank line followed by "- " at start of line
    lines = content.split('\n')
    processed_lines = []
    for i, line in enumerate(lines):
        processed_lines.append(line)
        # If current line starts with "- " and previous line is not blank and not a list item
        if (i > 0 and line.strip().startswith('- ') and 
            lines[i-1].strip() and not lines[i-1].strip().startswith('- ')):
            # Check if previous line ends with punctuation that suggests it's not a list continuation
            if not lines[i-1].rstrip().endswith((':', ';', ',')):
                # Insert blank line before this list
                processed_lines.insert(-1, '')
    
    content = '\n'.join(processed_lines)
    
    # 3. Convert internal .md file links to cross-references
    def replace_link(match):
        link_text = match.group(1)
        link_target = match.group(2)
        
        # Extract the file path (remove anchor if present)
        if '#' in link_target:
            file_path, anchor = link_target.split('#', 1)
        else:
            file_path = link_target
            anchor = None
        
        # Normalize path separators
        file_path = file_path.replace("\\", "/")
        
        # Look up the label
        if file_path in md_file_label_mapping:
            label = md_file_label_mapping[file_path]
            if anchor:
                # If there's an anchor, append it to the label
                anchor_slug = slugify(anchor)
                label = f"{label}-{anchor_slug}"
            # Return Pandoc-compatible cross-reference
            return f"[{link_text}](#{label})"
        else:
            # Keep original link if not found in mapping
            return match.group(0)
    
    content = MARKDOWN_LINK_RE.sub(replace_link, content)
    
    # 4. Add labels to headings (H1-H4) for cross-referencing
    def add_heading_label(match):
        level = len(match.group(1))
        heading_text = match.group(2).strip()
        
        # Extract label if already present: {#label}
        label_match = re.search(r'\s*\{#([^}]+)\}\s*$', heading_text)
        if label_match:
            # Label already present, keep it
            return match.group(0)
        
        # Generate label from heading text
        heading_slug = slugify(heading_text)
        # Combine with chapter ID for uniqueness
        label = f"sec:{chapter_id}-{heading_slug}"
        
        # Add label to heading
        return f"{match.group(1)} {heading_text} {{#{label}}}"
    
    content = HEADING_RE.sub(add_heading_label, content)
    
    return content

def post_process_latex(tex_file: Path, chapter_id: str, md_file_label_mapping: dict):
    """
    Post-processes LaTeX file after Pandoc conversion to add professional features:
    
    1. Fixes escaping issues:
       - Removes double-escaped ampersands (\textbackslash\& -> &)
       - Fixes escaped braces (\{ -> {, \} -> }) in code blocks and inline code
       - Ensures &&, {}, etc. display correctly in PDF
    
    2. Wraps Mermaid diagrams in figure environments:
       - Finds standalone \includegraphics{res/...} commands
       - Wraps them in \begin{figure}...\end{figure}
       - Adds captions from preceding headings
       - Adds labels (fig:chapter-diag001)
    
    3. Converts "Rule N:" patterns to algorithm environments:
       - Detects \paragraph{Rule N: ...} or \subsubsection{Rule N: ...} followed by lstlisting
       - Converts to \begin{algorithm}...\end{algorithm} with caption and label
       - Ensures algorithms appear in \listofalgorithms
    
    4. Adds labels and captions to code listings:
       - Finds all lstlisting blocks not inside algorithms
       - Adds caption from preceding heading (or generic "Code listing N")
       - Adds label (lst:chapter-heading-01)
       - Ensures listings appear in \lstlistoflistings
    
    All labels follow the pattern: {type}:{chapter_id}-{identifier}
    """
    content = tex_file.read_text(encoding="utf-8")
    original_content = content
    
    # Track listing counter for this chapter
    listing_counter = 0
    figure_counter = 0
    
    # 1. Fix escaping issues in code blocks
    # Fix double-escaped ampersands: \textbackslash\& -> &
    content = re.sub(
        r'\\textbackslash\\(&)',
        r'\1',
        content
    )
    
    # Fix escaped special characters in texttt (inline code)
    # Handle patterns like \texttt{\&\&} or \texttt{\textbackslash\&\textbackslash\&}
    def fix_texttt_escapes(match):
        texttt_content = match.group(1)
        
        # First, handle double-escaped patterns like \textbackslash\&
        texttt_content = texttt_content.replace(r'\textbackslash\&', '&')
        texttt_content = texttt_content.replace(r'\textbackslash\%', '%')
        texttt_content = texttt_content.replace(r'\textbackslash\_', '_')
        
        # Then handle single-escaped patterns
        texttt_content = texttt_content.replace(r'\{', '{')
        texttt_content = texttt_content.replace(r'\}', '}')
        texttt_content = texttt_content.replace(r'\&', '&')
        texttt_content = texttt_content.replace(r'\%', '%')
        texttt_content = texttt_content.replace(r'\_', '_')
        
        return r'\texttt{' + texttt_content + '}'
    
    # Match texttt - use non-greedy match to handle simple cases
    # For complex nested cases, we'll do multiple passes
    content = re.sub(
        r'\\texttt\{([^}]*?)\}',
        fix_texttt_escapes,
        content
    )
    
    # Fix escaped special characters inside lstlisting environments
    # Process lstlisting blocks carefully - unescape: \&, \%, \_, \{, \}
    def fix_lstlisting_content(match):
        begin_line = match.group(1)
        listing_content = match.group(2)
        end_line = match.group(3)
        
        # Fix all escaped special characters in the content (not in options)
        # Order matters: handle \{ and \} before other escapes
        listing_content = listing_content.replace(r'\{', '{')
        listing_content = listing_content.replace(r'\}', '}')
        listing_content = listing_content.replace(r'\&', '&')
        listing_content = listing_content.replace(r'\%', '%')
        listing_content = listing_content.replace(r'\_', '_')
        
        return begin_line + listing_content + end_line
    
    content = re.sub(
        r'(\\begin\{lstlisting\}(?:\[[^\]]*\])?)(.*?)(\\end\{lstlisting\})',
        fix_lstlisting_content,
        content,
        flags=re.DOTALL
    )
    
    # 2. Wrap Mermaid diagrams in figure environments
    # Find standalone includegraphics that reference res/ (Mermaid diagrams)
    # and wrap them in figure environments if not already wrapped
    def wrap_figure(match):
        nonlocal figure_counter
        figure_counter += 1
        
        includegraphics_line = match.group(0)
        # Extract the image path
        img_match = re.search(r'\\includegraphics(?:\[[^\]]*\])?\{([^}]+)\}', includegraphics_line)
        if img_match:
            img_path = img_match.group(1)
            if 'res/' not in img_path:
                return includegraphics_line  # Not a Mermaid diagram
            
            # Extract base name for label
            base_name = Path(img_path).stem
            label = f"fig:{chapter_id}-{base_name}"
            
            # Try to find a preceding heading for caption
            # Look backwards in the original content for nearest heading
            pos = match.start()
            preceding = original_content[:pos]
            # Look for the most recent heading
            heading_match = None
            for heading_pattern in [
                r'\\(?:sub)?(?:sub)?section\*?\{([^}]+)\}',
                r'\\paragraph\*?\{([^}]+)\}',
            ]:
                matches = list(re.finditer(heading_pattern, preceding))
                if matches:
                    heading_match = matches[-1]
                    break
            
            if heading_match:
                caption = heading_match.group(1)
            else:
                caption = f"Mermaid diagram {figure_counter}"
            
            return f"""\\begin{{figure}}[htbp]
  \\centering
  {includegraphics_line}
  \\caption{{{caption}}}
  \\label{{{label}}}
\\end{{figure}}"""
        return includegraphics_line
    
    # Match includegraphics that are not already inside figure environments
    # First, mark all includegraphics that are already in figures
    # Then process only those that aren't marked
    
    # Split content into parts, tracking whether we're inside a figure
    parts = []
    i = 0
    while i < len(content):
        # Check if we're entering a figure environment
        figure_start = content.find('\\begin{figure}', i)
        if figure_start == -1:
            # No more figures, add rest of content
            parts.append(('normal', content[i:]))
            break
        
        # Add content before figure
        if figure_start > i:
            parts.append(('normal', content[i:figure_start]))
        
        # Find the end of this figure
        figure_end = content.find('\\end{figure}', figure_start)
        if figure_end == -1:
            # Unclosed figure, treat rest as normal
            parts.append(('normal', content[figure_start:]))
            break
        
        # Add figure content (including the figure tags)
        parts.append(('figure', content[figure_start:figure_end + len('\\end{figure}')]))
        i = figure_end + len('\\end{figure}')
    
    # Process only the 'normal' parts
    processed_parts = []
    for part_type, part_content in parts:
        if part_type == 'figure':
            # Already in figure, keep as-is
            processed_parts.append(part_content)
        else:
            # Process includegraphics in this part
            processed_part = re.sub(
                r'\\includegraphics(?:\[[^\]]*\])?\{res/[^}]+\}',
                wrap_figure,
                part_content
            )
            processed_parts.append(processed_part)
    
    content = ''.join(processed_parts)
    
    # 3. Fix visual layout: convert \paragraph{} to block headings BEFORE processing algorithms
    # Convert \paragraph{...} to \subsubsection*{...} for better block layout
    def convert_paragraph_to_block_heading(match):
        para_content = match.group(1)
        label_part = match.group(2) if match.group(2) else ''
        
        # Extract the paragraph text
        text_match = re.search(r'\\paragraph\{([^}]+)\}', para_content)
        if text_match:
            heading_text = text_match.group(1)
            # Convert to subsubsection*
            return f'\\subsubsection*{{{heading_text}}}{label_part}'
        return match.group(0)
    
    # Match \paragraph{...} optionally followed by \label{...}
    content = re.sub(
        r'(\\paragraph\{[^}]+\})(\\label\{[^}]+\})?',
        convert_paragraph_to_block_heading,
        content
    )
    
    # 4. Process listings and detect "Rule N:" patterns
    # First, detect and convert "Rule N:" patterns to algorithms
    # Handle both \paragraph{Rule N:...} and \subsubsection{Rule N:...} (H4 headings)
    def convert_rule_pattern(match):
        rule_heading = match.group(1)
        lstlisting_block = match.group(2)
        
        # Extract rule number and title from either paragraph or subsubsection
        rule_match = re.search(r'\\(?:paragraph|subsubsection)\{Rule\s+(\d+):\s+([^}]+)\}', rule_heading)
        if not rule_match:
            return match.group(0)  # Return unchanged if pattern doesn't match
        
        rule_num = rule_match.group(1)
        rule_title = rule_match.group(2)
        label = f"alg:{chapter_id}-rule-{rule_num}-{slugify(rule_title)}"
        
        # Extract lstlisting content
        lst_content_match = re.search(r'\\begin\{lstlisting\}(?:\[[^\]]*\])?\n(.*?)\\end\{lstlisting\}', lstlisting_block, re.DOTALL)
        if lst_content_match:
            lst_options_match = re.search(r'\\begin\{lstlisting\}(\[[^\]]*\])?', lstlisting_block)
            lst_options = lst_options_match.group(1) if lst_options_match else ''
            lst_content = lst_content_match.group(1)
            
            return f"""\\begin{{algorithm}}[H]
  \\caption{{Rule {rule_num}: {rule_title}}}
  \\label{{{label}}}
  \\begin{{lstlisting}}{lst_options}
{lst_content}\\end{{lstlisting}}
\\end{{algorithm}}"""
        
        return match.group(0)
    
    # Pattern: paragraph/subsubsection{Rule N: ...} followed by lstlisting
    # Match heading, optional label, optional blank lines, then lstlisting
    content = re.sub(
        r'(\\(?:paragraph|subsubsection)\{Rule\s+\d+:[^}]+\}(?:\\label\{[^}]+\})?.*?\n)(.*?\\begin\{lstlisting\}.*?\\end\{lstlisting\})',
        convert_rule_pattern,
        content,
        flags=re.DOTALL
    )
    
    # 5. Add labels and captions to remaining listings (those not in algorithms)
    def add_listing_label_caption(match):
        nonlocal listing_counter
        
        begin_line = match.group(1)
        options = match.group(2) if match.group(2) else ''
        listing_content = match.group(3)
        end_line = match.group(4)
        
        # Check if this listing is already inside an algorithm (skip it)
        pos = match.start()
        preceding = content[:pos]
        if '\\begin{algorithm}' in preceding:
            # Find the most recent algorithm start
            algo_starts = [m.end() for m in re.finditer(r'\\begin\{algorithm\}', preceding)]
            algo_ends = [m.start() for m in re.finditer(r'\\end\{algorithm\}', preceding)]
            if algo_starts:
                last_algo_start = algo_starts[-1]
                # Check if there's a matching end after this start
                matching_ends = [e for e in algo_ends if e > last_algo_start]
                if not matching_ends or matching_ends[0] > pos:
                    # This listing is inside an algorithm, skip adding caption/label
                    return match.group(0)
        
        listing_counter += 1
        
        # Look backwards for a heading to use as caption
        caption = None
        label = None
        
        # Check previous content for heading
        heading_match = None
        for heading_pattern in [
            r'\\(?:sub)?(?:sub)?section\*?\{([^}]+)\}',
            r'\\paragraph\*?\{([^}]+)\}',
        ]:
            matches = list(re.finditer(heading_pattern, preceding))
            if matches:
                heading_match = matches[-1]
                break
        
        if heading_match:
            caption = heading_match.group(1)
            label = f"lst:{chapter_id}-{slugify(caption)}-{listing_counter:02d}"
        else:
            caption = f"Code listing {listing_counter}"
            label = f"lst:{chapter_id}-listing-{listing_counter:02d}"
        
        # Add caption and label to options
        if options:
            # Insert caption and label into existing options
            new_options = options.rstrip(']') + f', caption={{{caption}}}, label={{{label}}}]'
        else:
            new_options = f'[caption={{{caption}}}, label={{{label}}}]'
        
        return begin_line + new_options + '\n' + listing_content + end_line
    
    # Match lstlisting blocks that are not inside algorithms
    content = re.sub(
        r'(\\begin\{lstlisting\})(\[[^\]]*\])?\n(.*?)(\\end\{lstlisting\})',
        add_listing_label_caption,
        content,
        flags=re.DOTALL
    )
    
    # 6. Normalize language and style options to match .cls definitions
    # 
    # This ensures every lstlisting block has:
    # - Correct style= option matching the .cls style definitions
    # - Correct language= option matching the .cls language definitions
    # - Consistent naming (e.g., style=java with language=Java, not language=java)
    #
    # Mapping table: language from Pandoc -> {style, language} for LaTeX
    LANG_STYLE_MAP = {
        # Java
        "java": {"style": "java", "language": "Java"},
        "Java": {"style": "java", "language": "Java"},
        # C
        "c": {"style": "c", "language": "C"},
        "C": {"style": "c", "language": "C"},
        # FRISC / assembly
        "assembly": {"style": "frisc", "language": "frisc"},
        "asm": {"style": "frisc", "language": "frisc"},
        "frisc": {"style": "frisc", "language": "frisc"},
        # Pseudocode
        "pseudo": {"style": "pseudocode", "language": "pseudocode"},
        "pseudocode": {"style": "pseudocode", "language": "pseudocode"},
    }
    
    def normalize_lstlisting_options(match):
        begin_tag = match.group(1)
        options_str = match.group(2) if match.group(2) else ''
        listing_content = match.group(3)
        end_tag = match.group(4)
        
        # Parse options string into key-value pairs
        # Handle both language=value and language={value} formats
        options_dict = {}
        original_parts = []  # Keep original format for non-language/style options
        
        if options_str:
            # Remove brackets
            options_str = options_str.strip('[]')
            
            # Split by comma, but be careful about commas inside braces
            parts = []
            current_part = []
            brace_depth = 0
            
            for char in options_str:
                if char == '{':
                    brace_depth += 1
                    current_part.append(char)
                elif char == '}':
                    brace_depth -= 1
                    current_part.append(char)
                elif char == ',' and brace_depth == 0:
                    part_str = ''.join(current_part).strip()
                    if part_str:
                        parts.append(part_str)
                    current_part = []
                else:
                    current_part.append(char)
            
            if current_part:
                part_str = ''.join(current_part).strip()
                if part_str:
                    parts.append(part_str)
            
            # Parse each part
            for part in parts:
                part = part.strip()
                if not part:
                    continue
                
                original_parts.append(part)
                
                # Match key=value or key={value}
                kv_match = re.match(r'^(\w+)\s*=\s*(.+)$', part)
                if kv_match:
                    key = kv_match.group(1).strip()
                    value_str = kv_match.group(2).strip()
                    # Remove braces if present, but remember original format
                    value = value_str.strip('{}')
                    options_dict[key] = value
        
        # Determine language and style
        detected_language = None
        detected_style = None
        
        # Check if language is already in options
        if 'language' in options_dict:
            lang_key = options_dict['language']
            if lang_key in LANG_STYLE_MAP:
                mapping = LANG_STYLE_MAP[lang_key]
                detected_language = mapping['language']
                detected_style = mapping['style']
            else:
                # Unknown language, use generic
                detected_style = 'generic'
        else:
            # No language specified - try to detect from content
            if listing_content:
                # Check for FRISC assembly keywords
                if any(keyword in listing_content for keyword in ['MOVE', 'LOAD', 'STORE', 'CALL', 'RET', 'PUSH', 'POP', 'HALT', 'NOP']):
                    detected_language = 'frisc'
                    detected_style = 'frisc'
                # Could add more detection logic here for other languages
                else:
                    # Default to generic
                    detected_style = 'generic'
            else:
                # No content, default to generic
                detected_style = 'generic'
        
        # Build new options string
        new_options_parts = []
        
        # Add style first (if determined)
        if detected_style:
            new_options_parts.append(f'style={detected_style}')
        
        # Add language (if determined and not generic)
        if detected_language and detected_style != 'generic':
            new_options_parts.append(f'language={detected_language}')
        
        # Preserve other options (caption, label, etc.) in their original format
        for part in original_parts:
            # Check if this part is language or style (we'll replace these)
            part_stripped = part.strip()
            if part_stripped.startswith('language=') or part_stripped.startswith('style='):
                continue  # Skip, we'll add these with correct values
            # Keep all other options as-is
            new_options_parts.append(part)
        
        # Build final options string
        if new_options_parts:
            new_options = '[' + ', '.join(new_options_parts) + ']'
        else:
            new_options = ''
        
        return begin_tag + new_options + '\n' + listing_content + end_tag
    
    # Normalize all lstlisting blocks with proper style and language options
    # Use regex with careful handling of options containing braces
    # Pattern matches: \begin{lstlisting}[options]\ncontent\end{lstlisting}
    # Handles both [options]\n and just \n (no options)
    content = re.sub(
        r'(\\begin\{lstlisting\})(\[.*?\])?\s*\n(.*?)(\\end\{lstlisting\})',
        normalize_lstlisting_options,
        content,
        flags=re.DOTALL
    )
    
    # 7. Ensure blank line between headings and lstlisting for clean layout
    # Pattern: heading (with optional label) immediately followed by \begin{lstlisting}
    content = re.sub(
        r'(\\subsubsection\*?\{[^}]+\}(?:\\label\{[^}]+\})?)\s*\n(\\begin\{lstlisting\})',
        r'\1\n\n\2',
        content
    )
    content = re.sub(
        r'(\\paragraph\{[^}]+\}(?:\\label\{[^}]+\})?)\s*\n(\\begin\{lstlisting\})',
        r'\1\n\n\2',
        content
    )
    content = re.sub(
        r'(\\subsubsection\*?\{[^}]+\}(?:\\label\{[^}]+\})?)\s*\n(\\begin\{lstlisting\})',
        r'\1\n\n\2',
        content
    )
    
    if content != original_content:
        tex_file.write_text(content, encoding="utf-8")
        print(f"[post-process] Updated LaTeX with labels, captions, and fixes: {tex_file}")

def create_latex_class():
    """
    Creates the frisc-compiler-book.cls LaTeX class file with all styling,
    code listings, semantic environments, and custom title page.
    """
    cls_file = BOOK_DIR / "frisc-compiler-book.cls"
    
    cls_content = r"""\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{frisc-compiler-book}[2025/12/01 FRISC Compiler Book Class]

% Load base class
\LoadClass[11pt,a4paper]{book}

% ============================================================================
% PACKAGES
% ============================================================================

% Fonts and encoding
\RequirePackage[T1]{fontenc}
\RequirePackage[utf8]{inputenc}
\RequirePackage{lmodern}
\RequirePackage{microtype}

% Layout
\RequirePackage{geometry}
\geometry{margin=3cm,headheight=14pt}

% Graphics and images
\RequirePackage{graphicx}
\RequirePackage{adjustbox}
\RequirePackage{float}

% Math
\RequirePackage{amsmath,amssymb}

% Colors
\RequirePackage{xcolor}

% Hyperlinks (load last among packages that modify links)
\RequirePackage[colorlinks=true,
                linkcolor=blue!70!black,
                citecolor=blue!70!black,
                urlcolor=blue!70!black,
                filecolor=blue!70!black,
                pdfborder={0 0 0}]{hyperref}

% Unicode support
\RequirePackage{newunicodechar}
\RequirePackage{textcomp}
\RequirePackage{upquote}  % Better handling of quotes and special characters in verbatim

% Fancy verbatim for better line wrapping in verbatim environments
\RequirePackage{fancyvrb}

% Headers and footers
\RequirePackage{fancyhdr}
\RequirePackage{titling}

% Code listings with UTF-8 support
\RequirePackage{listings}
\RequirePackage{listingsutf8}

% Algorithms and pseudocode
\RequirePackage{algorithm}
\RequirePackage{algpseudocode}

% Colored boxes for definitions, theorems, etc.
\RequirePackage{tcolorbox}
\tcbuselibrary{breakable}
\tcbuselibrary{skins}
\tcbuselibrary{listings}  % For code blocks in tcolorbox

% Configure tcolorbox to inherit listings wrap behavior
\tcbset{
  listing options={
    breaklines=true,
    breakatwhitespace=false,
    columns=fullflexible,
    keepspaces=true,
  }
}

% ============================================================================
% COLOR DEFINITIONS
% ============================================================================

\definecolor{codebg}{RGB}{248,248,248}
\definecolor{codeframe}{RGB}{200,200,200}
\definecolor{javakeyword}{RGB}{0,0,128}
\definecolor{javastring}{RGB}{163,21,21}
\definecolor{javacomment}{RGB}{0,128,0}
\definecolor{javatype}{RGB}{128,0,128}
\definecolor{defbox}{RGB}{230,240,255}
\definecolor{defborder}{RGB}{50,100,200}
\definecolor{thmbox}{RGB}{255,248,230}
\definecolor{thmborder}{RGB}{200,150,50}
\definecolor{notebox}{RGB}{240,255,240}
\definecolor{noteborder}{RGB}{100,150,100}
\definecolor{warnbox}{RGB}{255,240,240}
\definecolor{warnborder}{RGB}{200,50,50}
\definecolor{exambox}{RGB}{255,250,240}
\definecolor{examborder}{RGB}{200,150,100}

% ============================================================================
% IMAGE HANDLING
% ============================================================================

\makeatletter
% Set default image dimensions
\setkeys{Gin}{width=0.85\textwidth,height=0.7\textheight,keepaspectratio}

% Redefine includegraphics to always center and respect margins
\let\@oldincludegraphics\includegraphics
\renewcommand{\includegraphics}[2][]{%
  \begin{center}
    \IfFileExists{#2}{%
      \adjustbox{max width=0.85\textwidth,max height=0.7\textheight,keepaspectratio,center}{\@oldincludegraphics[#1]{#2}}
    }{%
      \fbox{\textcolor{red}{Missing: #2}}
    }
  \end{center}
}
\makeatother

% Pandoc-specific command
\newcommand{\pandocbounded}[1]{#1}

% Handle missing images gracefully
\makeatletter
\def\Gin@extensions{.pdf,.png,.jpg,.jpeg,.mps,.eps}
\makeatother

% ============================================================================
% UNICODE CHARACTER HANDLING
% ============================================================================

% Box-drawing characters
\newunicodechar{├}{\textSFii}
\newunicodechar{─}{\textSFx}
\newunicodechar{│}{\textSFxi}
\newunicodechar{└}{\textSFviii}
\newunicodechar{┐}{\textSFiii}
\newunicodechar{┌}{\textSFi}
\newunicodechar{┘}{\textSFvii}
\newunicodechar{┴}{\textSFvi}

% Greek letters (lowercase)
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
\newunicodechar{ο}{\ensuremath{\omicron}}
\newunicodechar{π}{\ensuremath{\pi}}
\newunicodechar{ρ}{\ensuremath{\rho}}
\newunicodechar{σ}{\ensuremath{\sigma}}
\newunicodechar{τ}{\ensuremath{\tau}}
\newunicodechar{υ}{\ensuremath{\upsilon}}
\newunicodechar{φ}{\ensuremath{\phi}}
\newunicodechar{χ}{\ensuremath{\chi}}
\newunicodechar{ψ}{\ensuremath{\psi}}
\newunicodechar{ω}{\ensuremath{\omega}}

% Greek letters (uppercase)
\newunicodechar{Α}{\ensuremath{\Alpha}}
\newunicodechar{Β}{\ensuremath{\Beta}}
\newunicodechar{Γ}{\ensuremath{\Gamma}}
\newunicodechar{Δ}{\ensuremath{\Delta}}
\newunicodechar{Ε}{\ensuremath{\Epsilon}}
\newunicodechar{Ζ}{\ensuremath{\Zeta}}
\newunicodechar{Η}{\ensuremath{\Eta}}
\newunicodechar{Θ}{\ensuremath{\Theta}}
\newunicodechar{Ι}{\ensuremath{\Iota}}
\newunicodechar{Κ}{\ensuremath{\Kappa}}
\newunicodechar{Λ}{\ensuremath{\Lambda}}
\newunicodechar{Μ}{\ensuremath{\Mu}}
\newunicodechar{Ν}{\ensuremath{\Nu}}
\newunicodechar{Ξ}{\ensuremath{\Xi}}
\newunicodechar{Ο}{\ensuremath{\Omicron}}
\newunicodechar{Π}{\ensuremath{\Pi}}
\newunicodechar{Ρ}{\ensuremath{\Rho}}
\newunicodechar{Σ}{\ensuremath{\Sigma}}
\newunicodechar{Τ}{\ensuremath{\Tau}}
\newunicodechar{Υ}{\ensuremath{\Upsilon}}
\newunicodechar{Φ}{\ensuremath{\Phi}}
\newunicodechar{Χ}{\ensuremath{\Chi}}
\newunicodechar{Ψ}{\ensuremath{\Psi}}
\newunicodechar{Ω}{\ensuremath{\Omega}}

% Arrows (commonly used in compiler theory)
\newunicodechar{→}{\ensuremath{\rightarrow}}
\newunicodechar{⇒}{\ensuremath{\Rightarrow}}
\newunicodechar{←}{\ensuremath{\leftarrow}}
\newunicodechar{⇐}{\ensuremath{\Leftarrow}}
\newunicodechar{↔}{\ensuremath{\leftrightarrow}}
\newunicodechar{⇔}{\ensuremath{\Leftrightarrow}}
\newunicodechar{↦}{\ensuremath{\mapsto}}
\newunicodechar{⟶}{\ensuremath{\longrightarrow}}
\newunicodechar{⟹}{\ensuremath{\Longrightarrow}}
\newunicodechar{⟵}{\ensuremath{\longleftarrow}}
\newunicodechar{⟷}{\ensuremath{\longleftrightarrow}}
\newunicodechar{↪}{\ensuremath{\hookrightarrow}}
\newunicodechar{↩}{\ensuremath{\hookleftarrow}}

% Mathematical symbols and operators
\newunicodechar{·}{\ensuremath{\cdot}}
\newunicodechar{•}{\ensuremath{\bullet}}
\newunicodechar{∘}{\ensuremath{\circ}}
\newunicodechar{⋆}{\ensuremath{\star}}
\newunicodechar{×}{\ensuremath{\times}}
\newunicodechar{÷}{\ensuremath{\div}}
\newunicodechar{±}{\ensuremath{\pm}}
\newunicodechar{∓}{\ensuremath{\mp}}
\newunicodechar{≤}{\ensuremath{\leq}}
\newunicodechar{≥}{\ensuremath{\geq}}
\newunicodechar{≠}{\ensuremath{\neq}}
\newunicodechar{≈}{\ensuremath{\approx}}
\newunicodechar{≡}{\ensuremath{\equiv}}
\newunicodechar{≢}{\ensuremath{\not\equiv}}
\newunicodechar{∈}{\ensuremath{\in}}
\newunicodechar{∉}{\ensuremath{\notin}}
\newunicodechar{⊂}{\ensuremath{\subset}}
\newunicodechar{⊆}{\ensuremath{\subseteq}}
\newunicodechar{⊃}{\ensuremath{\supset}}
\newunicodechar{⊇}{\ensuremath{\supseteq}}
\newunicodechar{∪}{\ensuremath{\cup}}
\newunicodechar{∩}{\ensuremath{\cap}}
\newunicodechar{∅}{\ensuremath{\emptyset}}
\newunicodechar{∞}{\ensuremath{\infty}}
\newunicodechar{∑}{\ensuremath{\sum}}
\newunicodechar{∏}{\ensuremath{\prod}}
\newunicodechar{∫}{\ensuremath{\int}}
\newunicodechar{√}{\ensuremath{\sqrt}}
\newunicodechar{∂}{\ensuremath{\partial}}
\newunicodechar{∇}{\ensuremath{\nabla}}
\newunicodechar{∀}{\ensuremath{\forall}}
\newunicodechar{∃}{\ensuremath{\exists}}
\newunicodechar{∄}{\ensuremath{\nexists}}
\newunicodechar{∧}{\ensuremath{\land}}
\newunicodechar{∨}{\ensuremath{\lor}}
\newunicodechar{¬}{\ensuremath{\neg}}
\newunicodechar{⊕}{\ensuremath{\oplus}}
\newunicodechar{⊗}{\ensuremath{\otimes}}
\newunicodechar{⊥}{\ensuremath{\perp}}
\newunicodechar{⊤}{\ensuremath{\top}}
\newunicodechar{⊢}{\ensuremath{\vdash}}
\newunicodechar{⊨}{\ensuremath{\vDash}}
\newunicodechar{∥}{\ensuremath{\parallel}}
\newunicodechar{∦}{\ensuremath{\nparallel}}

% BNF and grammar symbols
\newunicodechar{⟨}{\ensuremath{\langle}}
\newunicodechar{⟩}{\ensuremath{\rangle}}
\newunicodechar{⟦}{\ensuremath{\llbracket}}
\newunicodechar{⟧}{\ensuremath{\rrbracket}}
\newunicodechar{⟪}{\ensuremath{\llangle}}
\newunicodechar{⟫}{\ensuremath{\rrangle}}
\newunicodechar{::=}{\ensuremath{::=}}
\newunicodechar{→}{\ensuremath{\rightarrow}}  % Already defined above, but for clarity
\newunicodechar{⇒}{\ensuremath{\Rightarrow}}  % Already defined above

% Special punctuation and symbols
\newunicodechar{…}{\ensuremath{\ldots}}
\newunicodechar{⋯}{\ensuremath{\cdots}}
\newunicodechar{⋮}{\ensuremath{\vdots}}
\newunicodechar{⋱}{\ensuremath{\ddots}}
\newunicodechar{′}{\ensuremath{^{\prime}}}
\newunicodechar{″}{\ensuremath{^{\prime\prime}}}
\newunicodechar{‴}{\ensuremath{^{\prime\prime\prime}}}
\newunicodechar{⁰}{\ensuremath{^0}}
\newunicodechar{¹}{\ensuremath{^1}}
\newunicodechar{²}{\ensuremath{^2}}
\newunicodechar{³}{\ensuremath{^3}}
\newunicodechar{⁴}{\ensuremath{^4}}
\newunicodechar{⁵}{\ensuremath{^5}}
\newunicodechar{⁶}{\ensuremath{^6}}
\newunicodechar{⁷}{\ensuremath{^7}}
\newunicodechar{⁸}{\ensuremath{^8}}
\newunicodechar{⁹}{\ensuremath{^9}}
\newunicodechar{₀}{\ensuremath{_0}}
\newunicodechar{₁}{\ensuremath{_1}}
\newunicodechar{₂}{\ensuremath{_2}}
\newunicodechar{₃}{\ensuremath{_3}}
\newunicodechar{₄}{\ensuremath{_4}}
\newunicodechar{₅}{\ensuremath{_5}}
\newunicodechar{₆}{\ensuremath{_6}}
\newunicodechar{₇}{\ensuremath{_7}}
\newunicodechar{₈}{\ensuremath{_8}}
\newunicodechar{₉}{\ensuremath{_9}}

% Additional symbols used in formal languages and automata
\newunicodechar{ε}{\ensuremath{\varepsilon}}  % Epsilon (empty string)
\newunicodechar{ϵ}{\ensuremath{\epsilon}}     % Alternative epsilon
\newunicodechar{ℕ}{\ensuremath{\mathbb{N}}}
\newunicodechar{ℤ}{\ensuremath{\mathbb{Z}}}
\newunicodechar{ℚ}{\ensuremath{\mathbb{Q}}}
\newunicodechar{ℝ}{\ensuremath{\mathbb{R}}}
\newunicodechar{ℂ}{\ensuremath{\mathbb{C}}}
\newunicodechar{𝔸}{\ensuremath{\mathbb{A}}}
\newunicodechar{𝔹}{\ensuremath{\mathbb{B}}}
\newunicodechar{ℙ}{\ensuremath{\mathbb{P}}}
\newunicodechar{Σ}{\ensuremath{\Sigma}}       % Alphabet symbol
\newunicodechar{Γ}{\ensuremath{\Gamma}}       % Stack alphabet
\newunicodechar{Δ}{\ensuremath{\Delta}}       % Transition function
\newunicodechar{δ}{\ensuremath{\delta}}       % Transition function (lowercase)
\newunicodechar{λ}{\ensuremath{\lambda}}      % Lambda
\newunicodechar{Λ}{\ensuremath{\Lambda}}      % Lambda (uppercase)
\newunicodechar{∅}{\ensuremath{\emptyset}}   % Empty set

% ============================================================================
% CODE LISTINGS CONFIGURATION
% ============================================================================
%
% LINE WRAPPING CONFIGURATION:
% All code blocks (listings, verbatim, tcolorbox) are configured to:
% - Automatically wrap long lines (breaklines=true)
% - Break inside long tokens when necessary (breakatwhitespace=false)
% - Never overflow page margins
% - Preserve Unicode characters correctly
%
% This ensures that code blocks like:
%   [A → αβγδεζηθικλμνξοπρστυφχψω, aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa]
%   SomeVeryLongIdentifierNameThatWouldNormallyGoOffThePageButMustWrapCorrectly
% will wrap correctly instead of overflowing.
%

% Base listings style with UTF-8 support and aggressive line wrapping
\lstset{
  inputencoding=utf8,
  extendedchars=true,
  basicstyle=\ttfamily\footnotesize,
  breaklines=true,              % Allow line wrapping
  breakatwhitespace=false,     % Allow breaking even inside long tokens (e.g., long identifiers)
  breakindent=0pt,              % No indentation for wrapped lines
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible,          % Better handling of monospaced text with wrapping
  keepspaces=true,              % Preserve spaces for alignment
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
  % Ensure listings respect page width and wrap properly
  aboveskip=\smallskipamount,
  belowskip=\smallskipamount,
  % Comprehensive literate table for compiler theory symbols
  literate=
    % Greek letters (lowercase)
    {α}{{\ensuremath{\alpha}}}1
    {β}{{\ensuremath{\beta}}}1
    {γ}{{\ensuremath{\gamma}}}1
    {δ}{{\ensuremath{\delta}}}1
    {ε}{{\ensuremath{\varepsilon}}}1
    {ζ}{{\ensuremath{\zeta}}}1
    {η}{{\ensuremath{\eta}}}1
    {θ}{{\ensuremath{\theta}}}1
    {ι}{{\ensuremath{\iota}}}1
    {κ}{{\ensuremath{\kappa}}}1
    {λ}{{\ensuremath{\lambda}}}1
    {μ}{{\ensuremath{\mu}}}1
    {ν}{{\ensuremath{\nu}}}1
    {ξ}{{\ensuremath{\xi}}}1
    {ο}{{\ensuremath{\omicron}}}1
    {π}{{\ensuremath{\pi}}}1
    {ρ}{{\ensuremath{\rho}}}1
    {σ}{{\ensuremath{\sigma}}}1
    {τ}{{\ensuremath{\tau}}}1
    {υ}{{\ensuremath{\upsilon}}}1
    {φ}{{\ensuremath{\phi}}}1
    {χ}{{\ensuremath{\chi}}}1
    {ψ}{{\ensuremath{\psi}}}1
    {ω}{{\ensuremath{\omega}}}1
    % Greek letters (uppercase)
    {Α}{{\ensuremath{\Alpha}}}1
    {Β}{{\ensuremath{\Beta}}}1
    {Γ}{{\ensuremath{\Gamma}}}1
    {Δ}{{\ensuremath{\Delta}}}1
    {Ε}{{\ensuremath{\Epsilon}}}1
    {Ζ}{{\ensuremath{\Zeta}}}1
    {Η}{{\ensuremath{\Eta}}}1
    {Θ}{{\ensuremath{\Theta}}}1
    {Ι}{{\ensuremath{\Iota}}}1
    {Κ}{{\ensuremath{\Kappa}}}1
    {Λ}{{\ensuremath{\Lambda}}}1
    {Μ}{{\ensuremath{\Mu}}}1
    {Ν}{{\ensuremath{\Nu}}}1
    {Ξ}{{\ensuremath{\Xi}}}1
    {Ο}{{\ensuremath{\Omicron}}}1
    {Π}{{\ensuremath{\Pi}}}1
    {Ρ}{{\ensuremath{\Rho}}}1
    {Σ}{{\ensuremath{\Sigma}}}1
    {Τ}{{\ensuremath{\Tau}}}1
    {Υ}{{\ensuremath{\Upsilon}}}1
    {Φ}{{\ensuremath{\Phi}}}1
    {Χ}{{\ensuremath{\Chi}}}1
    {Ψ}{{\ensuremath{\Psi}}}1
    {Ω}{{\ensuremath{\Omega}}}1
    % Arrows (critical for grammar definitions)
    {→}{{$\rightarrow$}}1
    {⇒}{{$\Rightarrow$}}1
    {←}{{$\leftarrow$}}1
    {⇐}{{$\Leftarrow$}}1
    {↔}{{$\leftrightarrow$}}1
    {⇔}{{$\Leftrightarrow$}}1
    {↦}{{$\mapsto$}}1
    {⟶}{{$\longrightarrow$}}1
    {⟹}{{$\Longrightarrow$}}1
    {⟵}{{$\longleftarrow$}}1
    {⟷}{{$\longleftrightarrow$}}1
    {↪}{{$\hookrightarrow$}}1
    {↩}{{$\hookleftarrow$}}1
    % Mathematical symbols and operators
    {·}{{$\cdot$}}1
    {•}{{$\bullet$}}1
    {∘}{{$\circ$}}1
    {⋆}{{$\star$}}1
    {×}{{$\times$}}1
    {÷}{{$\div$}}1
    {±}{{$\pm$}}1
    {∓}{{$\mp$}}1
    {≤}{{$\leq$}}1
    {≥}{{$\geq$}}1
    {≠}{{$\neq$}}1
    {≈}{{$\approx$}}1
    {≡}{{$\equiv$}}1
    {≢}{{$\not\equiv$}}1
    {∈}{{$\in$}}1
    {∉}{{$\notin$}}1
    {⊂}{{$\subset$}}1
    {⊆}{{$\subseteq$}}1
    {⊃}{{$\supset$}}1
    {⊇}{{$\supseteq$}}1
    {∪}{{$\cup$}}1
    {∩}{{$\cap$}}1
    {∅}{{$\emptyset$}}1
    {∞}{{$\infty$}}1
    {∑}{{$\sum$}}1
    {∏}{{$\prod$}}1
    {∫}{{$\int$}}1
    {√}{{$\sqrt$}}1
    {∂}{{$\partial$}}1
    {∇}{{$\nabla$}}1
    {∀}{{$\forall$}}1
    {∃}{{$\exists$}}1
    {∄}{{$\nexists$}}1
    {∧}{{$\land$}}1
    {∨}{{$\lor$}}1
    {¬}{{$\neg$}}1
    {⊕}{{$\oplus$}}1
    {⊗}{{$\otimes$}}1
    {⊥}{{$\perp$}}1
    {⊤}{{$\top$}}1
    {⊢}{{$\vdash$}}1
    {⊨}{{$\vDash$}}1
    {∥}{{$\parallel$}}1
    {∦}{{$\nparallel$}}1
    % BNF and grammar symbols (critical for parser theory)
    {⟨}{{$\langle$}}1
    {⟩}{{$\rangle$}}1
    {⟦}{{$\llbracket$}}1
    {⟧}{{$\rrbracket$}}1
    {⟪}{{$\llangle$}}1
    {⟫}{{$\rrangle$}}1
    % Special punctuation
    {…}{{$\ldots$}}1
    {⋯}{{$\cdots$}}1
    {⋮}{{$\vdots$}}1
    {⋱}{{$\ddots$}}1
    {′}{{$^{\prime}$}}1
    {″}{{$^{\prime\prime}$}}1
    {‴}{{$^{\prime\prime\prime}$}}1
    % Number superscripts and subscripts
    {⁰}{{$^0$}}1
    {¹}{{$^1$}}1
    {²}{{$^2$}}1
    {³}{{$^3$}}1
    {⁴}{{$^4$}}1
    {⁵}{{$^5$}}1
    {⁶}{{$^6$}}1
    {⁷}{{$^7$}}1
    {⁸}{{$^8$}}1
    {⁹}{{$^9$}}1
    {₀}{{$_0$}}1
    {₁}{{$_1$}}1
    {₂}{{$_2$}}1
    {₃}{{$_3$}}1
    {₄}{{$_4$}}1
    {₅}{{$_5$}}1
    {₆}{{$_6$}}1
    {₇}{{$_7$}}1
    {₈}{{$_8$}}1
    {₉}{{$_9$}}1
    % Additional symbols for formal languages
    {ε}{{$\varepsilon$}}1
    {ϵ}{{$\epsilon$}}1
    {ℕ}{{$\mathbb{N}$}}1
    {ℤ}{{$\mathbb{Z}$}}1
    {ℚ}{{$\mathbb{Q}$}}1
    {ℝ}{{$\mathbb{R}$}}1
    {ℂ}{{$\mathbb{C}$}}1
    {𝔸}{{$\mathbb{A}$}}1
    {𝔹}{{$\mathbb{B}$}}1
    {ℙ}{{$\mathbb{P}$}}1
    {Σ}{{$\Sigma$}}1
    {Γ}{{$\Gamma$}}1
    {Δ}{{$\Delta$}}1
    {δ}{{$\delta$}}1
    {λ}{{$\lambda$}}1
    {Λ}{{$\Lambda$}}1
    {∅}{{$\emptyset$}}1
}

% Java language definition
\lstdefinestyle{java}{
  language=Java,
  inputencoding=utf8,
  extendedchars=true,
  basicstyle=\ttfamily\footnotesize,
  breaklines=true,
  breakatwhitespace=false,  % Allow breaking inside long identifiers
  breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible,      % Better wrapping support
  keepspaces=true,
  frame=single,
  frameround=tttt,
  rulecolor=\color{codeframe},
  backgroundcolor=\color{codebg},
  keywordstyle=\color{javakeyword}\bfseries,
  commentstyle=\color{javacomment}\itshape,
  stringstyle=\color{javastring},
  morekeywords={class,interface,enum,extends,implements,public,private,protected,static,final,abstract,transient,volatile,synchronized,native,strictfp,package,import,throws,throw,try,catch,finally,if,else,switch,case,default,for,while,do,break,continue,return,new,this,super,instanceof,assert,const,goto,true,false,null},
  morecomment=[l]{//},
  morecomment=[s]{/*}{*/},
  morestring=[b]",
  morestring=[b]',
  numbers=left,
  numberstyle=\tiny\color{gray},
  numbersep=5pt,
  xleftmargin=15pt,
  xrightmargin=5pt,
  framexleftmargin=10pt,
  framexrightmargin=5pt,
  captionpos=b,
}

% C language definition
\lstdefinestyle{c}{
  language=C,
  inputencoding=utf8,
  extendedchars=true,
  basicstyle=\ttfamily\footnotesize,
  breaklines=true,
  breakatwhitespace=false,  % Allow breaking inside long identifiers
  breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible,      % Better wrapping support
  keepspaces=true,
  frame=single,
  frameround=tttt,
  rulecolor=\color{codeframe},
  backgroundcolor=\color{codebg},
  keywordstyle=\color{javakeyword}\bfseries,
  commentstyle=\color{javacomment}\itshape,
  stringstyle=\color{javastring},
  morekeywords={auto,break,case,char,const,continue,default,do,double,else,enum,extern,float,for,goto,if,int,long,register,return,short,signed,sizeof,static,struct,switch,typedef,union,unsigned,void,volatile,while},
  morecomment=[l]{//},
  morecomment=[s]{/*}{*/},
  morestring=[b]",
  morestring=[b]',
  numbers=left,
  numberstyle=\tiny\color{gray},
  numbersep=5pt,
  xleftmargin=15pt,
  xrightmargin=5pt,
  framexleftmargin=10pt,
  framexrightmargin=5pt,
  captionpos=b,
}

% FRISC assembly language definition
\lstdefinestyle{frisc}{
  inputencoding=utf8,
  extendedchars=true,
  basicstyle=\ttfamily\small,
  breaklines=true,
  breakatwhitespace=false,  % Allow breaking inside long tokens
  breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible,      % Better wrapping support
  keepspaces=true,
  frame=single,
  frameround=tttt,
  rulecolor=\color{codeframe},
  backgroundcolor=\color{codebg},
  keywordstyle=\color{javakeyword}\bfseries,
  commentstyle=\color{javacomment}\itshape,
  morekeywords={ADD, ADC, SUB, SBC, CMP, AND, OR, XOR, SHL, SHR, ASHR, ROTL, ROTR, MOVE, LOAD, LOADB, LOADH, STORE, STOREB, STOREH, PUSH, POP, JP, JP_C, JP_NC, JP_V, JP_NV, JP_N, JP_NN, JP_M, JP_P, JP_Z, JP_NZ, JP_EQ, JP_NE, JP_ULE, JP_UGT, JP_ULT, JP_UGE, JP_SLE, JP_SGT, JP_SLT, JP_SGE, JR, JR_C, JR_NC, JR_V, JR_NV, JR_N, JR_NN, JR_M, JR_P, JR_Z, JR_NZ, JR_EQ, JR_NE, JR_ULE, JR_UGT, JR_ULT, JR_UGE, JR_SLE, JR_SGT, JR_SLT, JR_SGE, CALL, CALL_C, CALL_NC, CALL_V, CALL_NV, CALL_N, CALL_NN, CALL_M, CALL_P, CALL_Z, CALL_NZ, CALL_EQ, CALL_NE, CALL_ULE, CALL_UGT, CALL_ULT, CALL_UGE, CALL_SLE, CALL_SGT, CALL_SLT, CALL_SGE, RET, RET_C, RET_NC, RET_V, RET_NV, RET_N, RET_NN, RET_M, RET_P, RET_Z, RET_NZ, RET_EQ, RET_NE, RET_ULE, RET_UGT, RET_ULT, RET_UGE, RET_SLE, RET_SGT, RET_SLT, RET_SGE, RETI, RETI_C, RETI_NC, RETI_V, RETI_NV, RETI_N, RETI_NN, RETI_M, RETI_P, RETI_Z, RETI_NZ, RETI_EQ, RETI_NE, RETI_ULE, RETI_UGT, RETI_ULT, RETI_UGE, RETI_SLE, RETI_SGT, RETI_SLT, RETI_SGE, RETN, RETN_C, RETN_NC, RETN_V, RETN_NV, RETN_N, RETN_NN, RETN_M, RETN_P, RETN_Z, RETN_NZ, RETN_EQ, RETN_NE, RETN_ULE, RETN_UGT, RETN_ULT, RETN_UGE, RETN_SLE, RETN_SGT, RETN_SLT, RETN_SGE, HALT, HALT_C, HALT_NC, HALT_V, HALT_NV, HALT_N, HALT_NN, HALT_M, HALT_P, HALT_Z, HALT_NZ, HALT_EQ, HALT_NE, HALT_ULE, HALT_UGT, HALT_ULT, HALT_UGE, HALT_SLE, HALT_SGT, HALT_SLT, HALT_SGE},
  morecomment=[l]{;},
  morecomment=[l]{//},
  numbers=left,
  numberstyle=\tiny\color{gray},
  numbersep=5pt,
  xleftmargin=15pt,
  xrightmargin=5pt,
  framexleftmargin=10pt,
  framexrightmargin=5pt,
  captionpos=b,
}

% Pseudocode style (simple monospace with keywords)
\lstdefinestyle{pseudocode}{
  inputencoding=utf8,
  extendedchars=true,
  basicstyle=\ttfamily\small,
  breaklines=true,
  breakatwhitespace=false,  % Allow breaking inside long tokens
  breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible,      % Better wrapping support
  keepspaces=true,
  frame=single,
  frameround=tttt,
  rulecolor=\color{codeframe},
  backgroundcolor=\color{codebg},
  keywordstyle=\color{javakeyword}\bfseries,
  commentstyle=\color{javacomment}\itshape,
  morekeywords={Algorithm,Input,Output,Step,If,Then,Else,End,For,While,Do,Return,Function,Procedure,Call},
  morecomment=[l]{//},
  morecomment=[s]{/*}{*/},
  numbers=left,
  numberstyle=\tiny\color{gray},
  numbersep=5pt,
  xleftmargin=15pt,
  xrightmargin=5pt,
  framexleftmargin=10pt,
  framexrightmargin=5pt,
  captionpos=b,
}

% Generic code style (for code blocks without language)
\lstdefinestyle{generic}{
  inputencoding=utf8,
  extendedchars=true,
  basicstyle=\ttfamily\footnotesize,
  breaklines=true,
  breakatwhitespace=false,  % Allow breaking inside long tokens
  breakindent=0pt,
  postbreak=\mbox{\textcolor{gray}{$\hookrightarrow$}\space},
  columns=fullflexible,      % Better wrapping support
  keepspaces=true,
  frame=single,
  frameround=tttt,
  rulecolor=\color{codeframe},
  backgroundcolor=\color{codebg},
  numbers=left,
  numberstyle=\tiny\color{gray},
  numbersep=5pt,
  xleftmargin=15pt,
  xrightmargin=5pt,
  framexleftmargin=10pt,
  framexrightmargin=5pt,
  captionpos=b,
}

% Define custom languages for FRISC and pseudocode
% FRISC assembly language
\lstdefinelanguage{frisc}{
  keywords={ADD, ADC, SUB, SBC, CMP, AND, OR, XOR, SHL, SHR, ASHR, ROTL, ROTR, MOVE, LOAD, LOADB, LOADH, STORE, STOREB, STOREH, PUSH, POP, JP, JP_C, JP_NC, JP_V, JP_NV, JP_N, JP_NN, JP_M, JP_P, JP_Z, JP_NZ, JP_EQ, JP_NE, JP_ULE, JP_UGT, JP_ULT, JP_UGE, JP_SLE, JP_SGT, JP_SLT, JP_SGE, JR, JR_C, JR_NC, JR_V, JR_NV, JR_N, JR_NN, JR_M, JR_P, JR_Z, JR_NZ, JR_EQ, JR_NE, JR_ULE, JR_UGT, JR_ULT, JR_UGE, JR_SLE, JR_SGT, JR_SLT, JR_SGE, CALL, CALL_C, CALL_NC, CALL_V, CALL_NV, CALL_N, CALL_NN, CALL_M, CALL_P, CALL_Z, CALL_NZ, CALL_EQ, CALL_NE, CALL_ULE, CALL_UGT, CALL_ULT, CALL_UGE, CALL_SLE, CALL_SGT, CALL_SLT, CALL_SGE, RET, RET_C, RET_NC, RET_V, RET_NV, RET_N, RET_NN, RET_M, RET_P, RET_Z, RET_NZ, RET_EQ, RET_NE, RET_ULE, RET_UGT, RET_ULT, RET_UGE, RET_SLE, RET_SGT, RET_SLT, RET_SGE, RETI, RETI_C, RETI_NC, RETI_V, RETI_NV, RETI_N, RETI_NN, RETI_M, RETI_P, RETI_Z, RETI_NZ, RETI_EQ, RETI_NE, RETI_ULE, RETI_UGT, RETI_ULT, RETI_UGE, RETI_SLE, RETI_SGT, RETI_SLT, RETI_SGE, RETN, RETN_C, RETN_NC, RETN_V, RETN_NV, RETN_N, RETN_NN, RETN_M, RETN_P, RETN_Z, RETN_NZ, RETN_EQ, RETN_NE, RETN_ULE, RETN_UGT, RETN_ULT, RETN_UGE, RETN_SLE, RETN_SGT, RETN_SLT, RETN_SGE, HALT, HALT_C, HALT_NC, HALT_V, HALT_NV, HALT_N, HALT_NN, HALT_M, HALT_P, HALT_Z, HALT_NZ, HALT_EQ, HALT_NE, HALT_ULE, HALT_UGT, HALT_ULT, HALT_UGE, HALT_SLE, HALT_SGT, HALT_SLT, HALT_SGE},
  comment=[l]{;},
  comment=[l]{//},
  sensitive=false,
}

% Pseudocode language
\lstdefinelanguage{pseudocode}{
  keywords={Algorithm,Input,Output,Step,If,Then,Else,End,For,While,Do,Return,Function,Procedure,Call},
  comment=[l]{//},
  comment=[s]{/*}{*/},
  sensitive=false,
}

% Map language names to styles
% When listings encounters language=java, it will use the Java language definition
% and we want it to also use style=java. We can do this by setting the default
% style for each language, or by post-processing pandoc output.
% For now, we'll rely on pandoc's --listings to output the correct language parameter,
% and listings will handle Java and C automatically.
% For FRISC and pseudocode, we've defined custom languages above.

% List of listings configuration
\renewcommand{\lstlistingname}{Code Listing}
\renewcommand{\lstlistlistingname}{List of Code Listings}

% ============================================================================
% ALGORITHM ENVIRONMENT CONFIGURATION
% ============================================================================

\floatname{algorithm}{Algorithm}
\renewcommand{\algorithmicrequire}{\textbf{Input:}}
\renewcommand{\algorithmicensure}{\textbf{Output:}}
\renewcommand{\algorithmiccomment}[1]{\hfill\textit{// #1}}

% Style algorithm boxes
\makeatletter
\renewcommand{\ALG@beginalgorithmic}{\small}
\makeatother

% ============================================================================
% SEMANTIC ENVIRONMENTS (Definitions, Theorems, Notes, etc.)
% ============================================================================

% Definition environment
\newtcolorbox{definition}[1][]{
  colback=defbox,
  colframe=defborder,
  fonttitle=\bfseries,
  title=Definition,
  breakable,
  enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=defborder,colframe=defborder},
  #1
}

% Theorem environment
\newtcolorbox{theorem}[1][]{
  colback=thmbox,
  colframe=thmborder,
  fonttitle=\bfseries,
  title=Theorem,
  breakable,
  enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=thmborder,colframe=thmborder},
  #1
}

% Note environment
\newtcolorbox{note}[1][]{
  colback=notebox,
  colframe=noteborder,
  fonttitle=\bfseries,
  title=Note,
  breakable,
  enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=noteborder,colframe=noteborder},
  #1
}

% Warning environment
\newtcolorbox{warning}[1][]{
  colback=warnbox,
  colframe=warnborder,
  fonttitle=\bfseries,
  title=Warning,
  breakable,
  enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=warnborder,colframe=warnborder},
  #1
}

% Example environment
\newtcolorbox{example}[1][]{
  colback=exambox,
  colframe=examborder,
  fonttitle=\bfseries,
  title=Example,
  breakable,
  enhanced,
  attach boxed title to top left={yshift=-2mm,xshift=3mm},
  boxed title style={colback=examborder,colframe=examborder},
  #1
}

% ============================================================================
% PANDOC COMPATIBILITY
% ============================================================================

% Pandoc uses \tightlist for compact lists
\providecommand{\tightlist}{%
  \setlength{\itemsep}{0pt}\setlength{\parskip}{0pt}}

% ============================================================================
% FORMATTING IMPROVEMENTS
% ============================================================================

% Reduce overfull/underfull boxes
\sloppy
\tolerance=1000
\emergencystretch=3em
\hfuzz=0.1pt

% Better verbatim handling with Unicode support and line wrapping
\makeatletter
\def\verbatim@font{\ttfamily\small}
% Ensure verbatim environments preserve Unicode
% upquote is already loaded above
\makeatother

% Configure fancyvrb for verbatim environments with line wrapping
\fvset{
  breaklines=true,        % Wrap long lines
  breakanywhere=true,     % Allow breaking inside tokens (e.g., long identifiers)
  breakautoindent=true,    % Preserve indentation on wrapped lines
  baselinestretch=1.0,    % Normal line spacing
  fontsize=\small,        % Match listings font size
}

% Replace standard verbatim with fancyvrb's Verbatim for better wrapping
% This ensures all verbatim environments (including those from Pandoc) wrap correctly
\let\verbatim\Verbatim
\let\endverbatim\endVerbatim

% Also ensure SaveVerbatim and BVerbatim wrap
\fvset{commandchars=\\\{\}}

% Inline code (texttt) with Unicode support
% Pandoc converts inline code to \texttt, which should work with newunicodechar
% The newunicodechar definitions above will handle Unicode in \texttt automatically

% ============================================================================
% CUSTOM TITLE PAGE
% ============================================================================

\renewcommand{\maketitle}{%
  \begin{titlepage}
    \centering
    \vspace*{2cm}
    
    % Main title
    {\Huge\bfseries \thetitle\par}
    \vspace{1.5cm}
    
    % Subtitle (if provided via \subtitle command)
    \ifdefined\thesubtitle
      {\Large \thesubtitle\par}
      \vspace{1cm}
    \fi
    
    % Decorative line
    \rule{0.6\textwidth}{0.4pt}
    \vspace{1.5cm}
    
    % Author
    {\Large \theauthor\par}
    \vspace{0.5cm}
    
    % Optional tagline
    {\large A C Subset Compiler for the FRISC Architecture\par}
    \vfill
    
    % Date
    \ifx\thedate\empty
      {\large \today\par}
    \else
      {\large \thedate\par}
    \fi
    
    % Optional version
    \ifdefined\theversion
      \vspace{0.5cm}
      {\normalsize Version \theversion\par}
    \fi
  \end{titlepage}
}

% Optional subtitle command
\newcommand{\subtitle}[1]{\newcommand{\thesubtitle}{#1}}

% Optional version command
\newcommand{\version}[1]{\newcommand{\theversion}{#1}}

% ============================================================================
% LISTS OF FIGURES, TABLES, LISTINGS, ALGORITHMS
% ============================================================================

% Ensure proper spacing and formatting for all lists
\makeatletter
\renewcommand{\@tocrmarg}{2.55em plus 1fil}
\makeatother

% Configure list of listings
\renewcommand{\lstlistoflistings}{\begingroup
  \tocfile{\lstlistlistingname}{lol}
\endgroup}

% ============================================================================
% HEADERS AND FOOTERS
% ============================================================================

\pagestyle{fancy}
\fancyhf{}
\fancyhead[LE]{\leftmark}
\fancyhead[RO]{\rightmark}
\fancyfoot[C]{\thepage}
\renewcommand{\headrulewidth}{0.4pt}
\renewcommand{\footrulewidth}{0pt}

% Clear headers on chapter pages
\fancypagestyle{plain}{%
  \fancyhf{}
  \fancyfoot[C]{\thepage}
  \renewcommand{\headrulewidth}{0pt}
}

% ============================================================================
% END OF CLASS
% ============================================================================

\endinput
"""
    
    cls_file.write_text(cls_content, encoding="utf-8")
    print(f"[class] Generated {cls_file}")

def run_pandoc(input_md: Path, output_tex: Path):
    """
    Pokreće pandoc da pretvori markdown u LaTeX fragment (bez preambule).
    Koristi --listings flag da bi code blokovi bili pretvoreni u lstlisting okruženja.
    Preserves all Unicode characters including Greek letters, arrows, and special symbols.
    """
    try:
        subprocess.run(
            [
                "pandoc",
                "--from", "markdown+raw_tex+tex_math_single_backslash+lists_without_preceding_blankline",
                "--to", "latex",
                "--listings",  # Use listings package for code blocks
                "--wrap=none",  # Don't wrap lines to avoid breaking code blocks
                "--preserve-tabs",  # Preserve tabs in code blocks
                "-o", str(output_tex),
                str(input_md),
            ],
            check=True,
            encoding="utf-8",  # Explicitly use UTF-8 encoding
            errors="replace"  # Replace any encoding errors instead of failing
        )
        print(f"[pandoc] Generated {output_tex}")
        
        # Post-process the LaTeX to handle pseudocode blocks and other enhancements
        post_process_pseudocode(output_tex)
        
    except FileNotFoundError:
        print("ERROR: 'pandoc' not found. Install pandoc and ensure it is in PATH.")
        raise
    except subprocess.CalledProcessError as e:
        print(f"ERROR: pandoc failed for {input_md}: {e}")
        raise

def post_process_pseudocode(tex_file: Path):
    """
    Post-processes LaTeX file to add style parameters to lstlisting blocks
    for custom languages (frisc, pseudocode, etc.) so they use the correct styling.
    """
    content = tex_file.read_text(encoding="utf-8")
    original_content = content
    
    # Add style parameter for FRISC assembly
    # Pattern: [language=frisc] or [language=asm] -> [language=frisc,style=frisc]
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=(frisc|asm)\]',
        r'\\begin{lstlisting}[language=\1,style=frisc]',
        content
    )
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=\{(frisc|asm)\}\]',
        r'\\begin{lstlisting}[language={\1},style=frisc]',
        content
    )
    
    # Add style parameter for pseudocode
    # Pattern: [language=pseudocode] or [language=pseudo] -> [language=pseudocode,style=pseudocode]
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=(pseudocode|pseudo)\]',
        r'\\begin{lstlisting}[language=pseudocode,style=pseudocode]',
        content
    )
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=\{(pseudocode|pseudo)\}\]',
        r'\\begin{lstlisting}[language={pseudocode},style=pseudocode]',
        content
    )
    
    # Add style parameter for Java (ensure it uses java style)
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=java\]',
        r'\\begin{lstlisting}[language=Java,style=java]',
        content
    )
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=\{java\}\]',
        r'\\begin{lstlisting}[language={Java},style=java]',
        content
    )
    
    # Add style parameter for C
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=c\]',
        r'\\begin{lstlisting}[language=C,style=c]',
        content
    )
    content = re.sub(
        r'\\begin\{lstlisting\}\[language=\{c\}\]',
        r'\\begin{lstlisting}[language={C},style=c]',
        content
    )
    
    if content != original_content:
        tex_file.write_text(content, encoding="utf-8")
        print(f"[post-process] Updated code block styles in {tex_file}")

def create_main_tex(chapter_entries):
    """
    Generira main.tex koji uključuje sva poglavlja iz 'chapter_entries'.
    chapter_entries: lista tupleova (chapter_dir_name, chapter_title, tex_filename)
    """
    main_tex = BOOK_DIR / "main.tex"

    lines = []
    # Use the custom class
    lines.append(r"\documentclass{frisc-compiler-book}")
    lines.append("")
    
    # Title and metadata
    lines.append(r"\title{Building a C Subset Compiler for the FRISC Architecture}")
    lines.append(r"\subtitle{FRISCcc: The C Compiler for the FRISC Architecture}")
    lines.append(r"\author{Karlo Knežević, Ph.D.}")
    lines.append(r"\date{\today}")
    lines.append(r"\version{0.8}")
    lines.append("")
    
    # Document structure
    lines.append(r"\begin{document}")
    lines.append(r"\frontmatter")
    lines.append(r"\maketitle")
    lines.append("")
    
    # Front matter lists
    lines.append(r"\tableofcontents")
    lines.append(r"\listoffigures")
    lines.append(r"\listoftables")
    lines.append(r"\lstlistoflistings")
    lines.append(r"\listofalgorithms")
    lines.append("")
    
    lines.append(r"\mainmatter")
    lines.append("")

    for dir_name, chapter_title, tex_filename in chapter_entries:
        lines.append(f"% Chapter from {dir_name}")
        lines.append(r"\chapter{" + chapter_title + "}")
        lines.append(r"\input{" + tex_filename.replace("\\", "/") + "}")
        lines.append("")

    lines.append(r"\end{document}")
    lines.append("")

    main_tex.write_text("\n".join(lines), encoding="utf-8")
    print(f"[main.tex] Generated {main_tex}")

def compile_latex():
    """
    Kompajlira main.tex u PDF sa više prolaza za rješavanje cross-referenci.
    Tipično treba 3-4 prolaza: pdflatex -> pdflatex -> makeindex (opcionalno) -> pdflatex -> pdflatex
    """
    cwd = os.getcwd()
    try:
        os.chdir(BOOK_DIR)
        if not Path("main.tex").exists():
            print("No main.tex found to compile.")
            return
        
        try:
            # First pass: Generate .aux file with references
            print("[pdflatex] Pass 1/4: Initial compilation...")
            result1 = subprocess.run(
                ["pdflatex", "-interaction=nonstopmode", "main.tex"],
                capture_output=True,
                text=False  # Capture as bytes to handle encoding issues
            )
            # Decode with error handling
            stdout1 = result1.stdout.decode('utf-8', errors='replace') if result1.stdout else ""
            stderr1 = result1.stderr.decode('utf-8', errors='replace') if result1.stderr else ""
            
            if result1.returncode != 0:
                print(f"WARNING: First pass had errors (this is often normal)")
                # Check if PDF was still generated despite errors
                if not Path("main.pdf").exists():
                    print("ERROR: PDF not generated. Check main.log for details.")
                    print("Last 20 lines of output:")
                    print(stdout1[-500:] if stdout1 else stderr1[-500:])
                    return
            
            # Second pass: Resolve references
            print("[pdflatex] Pass 2/4: Resolving references...")
            subprocess.run(
                ["pdflatex", "-interaction=nonstopmode", "main.tex"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            
            # Third pass: Final resolution (some references need 3 passes)
            print("[pdflatex] Pass 3/4: Finalizing references...")
            subprocess.run(
                ["pdflatex", "-interaction=nonstopmode", "main.tex"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
            
            # Fourth pass: Ensure everything is stable
            print("[pdflatex] Pass 4/4: Final compilation...")
            result = subprocess.run(
                ["pdflatex", "-interaction=nonstopmode", "main.tex"],
                capture_output=True,
                text=False  # Capture as bytes to handle encoding issues
            )
            # Decode with error handling
            stdout = result.stdout.decode('utf-8', errors='replace') if result.stdout else ""
            stderr = result.stderr.decode('utf-8', errors='replace') if result.stderr else ""
            
            # Check if PDF was generated
            if Path("main.pdf").exists():
                pdf_size = Path("main.pdf").stat().st_size
                # Try to get page count from log
                try:
                    log_content = Path("main.log").read_text(encoding="utf-8", errors="ignore")
                    page_match = re.search(r'Output written on main\.pdf \((\d+) pages', log_content)
                    pages = page_match.group(1) if page_match else "?"
                    print(f"✓ Compilation successful! PDF generated: main.pdf ({pages} pages, {pdf_size:,} bytes)")
                except:
                    print(f"✓ Compilation successful! PDF generated: main.pdf ({pdf_size:,} bytes)")
            else:
                print("ERROR: Compilation completed but PDF not found.")
                if stderr:
                    print("Error output:")
                    print(stderr[-1000:])
                
        except FileNotFoundError:
            print("WARNING: 'pdflatex' not found. Install LaTeX or compile main.tex manually.")
            print("         On macOS: brew install --cask mactex")
            print("         On Linux: sudo apt-get install texlive-full")
        except subprocess.CalledProcessError as e:
            print(f"ERROR: pdflatex failed: {e}")
            print("       Check main.log for details.")
    finally:
        os.chdir(cwd)

def main():
    if not DOCS_DIR.exists() or not DOCS_DIR.is_dir():
        print(f"ERROR: {DOCS_DIR} directory not found. Run this script from project root.")
        return

    # Očisti i napravi book strukturu
    if BOOK_DIR.exists():
        print(f"[clean] Removing existing {BOOK_DIR}")
        shutil.rmtree(BOOK_DIR)

    CHAPTERS_DIR.mkdir(parents=True, exist_ok=True)
    RES_DIR.mkdir(parents=True, exist_ok=True)

    # Generate the LaTeX class file
    create_latex_class()

    # Build mapping from markdown file paths to LaTeX labels
    md_file_label_mapping = build_md_file_label_mapping(DOCS_DIR)
    print(f"[mapping] Built {len(md_file_label_mapping)} markdown file to label mappings")

    chapter_entries = []

    # Sortiraj direktorije u docs po imenu (01-introduction, 02-..., ...)
    for chapter_dir in sorted(DOCS_DIR.iterdir()):
        if not chapter_dir.is_dir():
            continue

        dir_name = chapter_dir.name  # npr. "01-introduction"
        # Iz imena foldera izvući naslov poglavlja
        # Ukloni vodeći broj i crticu, "01-introduction" -> "introduction"
        title_part = re.sub(r"^\d+\-?", "", dir_name)
        title_part = title_part.replace("-", " ")
        chapter_title = title_part.strip().title() if title_part.strip() else dir_name

        # Pripremi .md i .tex za ovo poglavlje
        chapter_md = CHAPTERS_DIR / f"{dir_name}.md"
        chapter_tex = CHAPTERS_DIR / f"{dir_name}.tex"

        all_md_texts = []

        # Uzmi sve .md fileove u folderu, sortirano
        md_files = sorted(chapter_dir.glob("*.md"))
        if not md_files:
            # Ako nema md fileova, preskoči poglavlje
            print(f"[skip] No .md files in {chapter_dir}")
            continue

        for md_file in md_files:
            raw_md = md_file.read_text(encoding="utf-8")
            prefix = f"{dir_name}_{md_file.stem}"
            
            # Extract Mermaid diagrams and get figure info
            processed_md, figure_info_list = extract_and_replace_mermaid(raw_md, prefix)
            
            # Preprocess markdown (language mapping, bullet lists, internal links, heading labels)
            processed_md = preprocess_markdown(processed_md, md_file_label_mapping, dir_name)
            
            all_md_texts.append(processed_md)

        # For appendices chapter, add config files
        if dir_name == "12-appendices":
            config_dir = Path("config")
            if config_dir.exists():
                config_files = [
                    ("lexer_definition.txt", "Lexer Definition"),
                    ("parser_definition.txt", "Parser Definition"),
                    ("semantics_definition.txt", "Semantics Definition"),
                ]
                
                for config_file_name, section_title in config_files:
                    config_file_path = config_dir / config_file_name
                    if config_file_path.exists():
                        try:
                            config_content = config_file_path.read_text(encoding="utf-8")
                            # Escape triple backticks if present (rare but possible)
                            config_content = config_content.replace("```", "\\`\\`\\`")
                            
                            # Append as a code block section
                            config_markdown = f"\n\n## {section_title}\n\n```text\n{config_content}\n```\n"
                            all_md_texts.append(config_markdown)
                            print(f"[appendices] Added {config_file_name} to appendices chapter")
                        except Exception as e:
                            print(f"WARNING: Could not read {config_file_path}: {e}")

        # Spoji sve md u jedan za poglavlje
        chapter_md.write_text("\n\n".join(all_md_texts) + "\n", encoding="utf-8")

        # Pretvori poglavlje u .tex
        run_pandoc(chapter_md, chapter_tex)
        
        # Post-process LaTeX (add labels, captions, fix escaping, wrap algorithms)
        post_process_latex(chapter_tex, dir_name, md_file_label_mapping)

        # Zabilježi za main.tex
        # putanja u odnosu na main.tex (koji je u book/)
        rel_tex_path = CHAPTERS_DIR.relative_to(BOOK_DIR) / chapter_tex.name
        chapter_entries.append((dir_name, chapter_title, str(rel_tex_path)))

    # Generiraj main.tex
    create_main_tex(chapter_entries)

    # Pokušaj kompajlirati u PDF
    compile_latex()

    print("\nDone. Generated LaTeX book in 'book/' directory.")
    print("You can compile manually with:")
    print("  cd book && pdflatex main.tex")

if __name__ == "__main__":
    main()
