#!/usr/bin/env bash

set -euo pipefail

BASE="${BASE:-/Users/karloknezevic/Desktop/ccompiler/data}"

SRC_ROOT="$BASE/test"

LEX_DST="compiler-lexer/src/test/resources"
PAR_DST="compiler-parser/src/test/resources"
SEM_DST="compiler-semantics/src/test/resources"
CG_DST="compiler-codegen/src/test/resources"
CLI_DST="cli/src/test/resources"

shopt -s nullglob

i=0
for dir in "$SRC_ROOT"/program*; do
  [[ -d "$dir" ]] || continue
  
  case_id=$(printf "ppjc_case_%02d" "$i")
  
  prog="$dir/program.c"
  lex="$dir/leksicke_jedinke.txt"
  gen="$dir/generativno_stablo.txt"
  syn="$dir/sintaksno_stablo.txt"
  sem="$dir/semantic.txt"
  frisc="$dir/ocekivani_frisc.s"
  
  for dst in "$LEX_DST" "$PAR_DST" "$SEM_DST" "$CG_DST" "$CLI_DST"; do
    mkdir -p "$dst/$case_id"
    [[ -f "$prog" ]] && cp -f "$prog" "$dst/$case_id/program.c"
  done
  
  [[ -f "$lex"  ]] && cp -f "$lex"  "$LEX_DST/$case_id/leksicke_jedinke.txt"
  [[ -f "$gen"  ]] && cp -f "$gen"  "$PAR_DST/$case_id/generativno_stablo.txt"
  [[ -f "$syn"  ]] && cp -f "$syn"  "$PAR_DST/$case_id/sintaksno_stablo.txt"
  [[ -f "$sem"  ]] && cp -f "$sem"  "$SEM_DST/$case_id/semantic.txt"
  [[ -f "$frisc" ]] && cp -f "$frisc" "$CG_DST/$case_id/ocekivani_frisc.s"
  
  ((i++))
done

echo "Imported $i case(s) from $SRC_ROOT."

