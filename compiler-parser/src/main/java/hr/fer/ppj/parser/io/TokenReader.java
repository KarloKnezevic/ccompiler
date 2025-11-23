package hr.fer.ppj.parser.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads tokens from lexer output format.
 * 
 * <p>Expected format:
 * <pre>
 * tablica znakova:
 * indeks   uniformni znak   izvorni tekst
 *     0   KR_INT            int
 *     ...
 * niz uniformnih znakova:
 * uniformni znak    redak    indeks u tablicu znakova
 * KR_INT               1       0
 * ...
 * </pre>
 * 
 * <p>Or simplified format:
 * <pre>
 * UNIFORMNI_ZNAK REDAK LEKSIČKA_JEDINKA
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TokenReader {
  
  /**
   * Represents a token with its type, line number, and lexical unit.
   */
  public record Token(String type, int line, String lexicalUnit) {}
  
  /**
   * Reads tokens from lexer output.
   * 
   * @param reader The input reader
   * @return List of tokens
   * @throws IOException If reading fails
   */
  public List<Token> readTokens(Reader reader) throws IOException {
    // Read all lines first (since we might need to parse twice)
    List<String> lines = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(reader)) {
      String line;
      while ((line = br.readLine()) != null) {
        lines.add(line);
      }
    }
    
    // Try full format first
    Map<Integer, String> symbolTable = new HashMap<>();
    List<Token> tokens = new ArrayList<>();
    boolean inSymbolTable = false;
    boolean inTokenStream = false;
    
    for (String line : lines) {
      line = line.trim();
      
      if (line.isEmpty()) {
        continue;
      }
      
      // Check for section markers
      if (line.equals("tablica znakova:")) {
        inSymbolTable = true;
        inTokenStream = false;
        continue;
      }
      
      if (line.equals("niz uniformnih znakova:")) {
        inSymbolTable = false;
        inTokenStream = true;
        continue;
      }
      
      // Skip header lines
      if (line.startsWith("indeks") || line.startsWith("uniformni znak")) {
        continue;
      }
      
      // Parse symbol table entries
      if (inSymbolTable) {
        parseSymbolTableEntry(line, symbolTable);
      }
      
      // Parse token stream entries
      if (inTokenStream) {
        Token token = parseTokenStreamEntry(line, symbolTable);
        if (token != null) {
          tokens.add(token);
        }
      }
    }
    
    // If no token stream section found, try simplified format
    if (tokens.isEmpty()) {
      return readSimplifiedFormat(lines);
    }
    
    return tokens;
  }
  
  /**
   * Parses a symbol table entry: "     0   KR_INT            int"
   */
  private void parseSymbolTableEntry(String line, Map<Integer, String> symbolTable) {
    // Format: "     0   KR_INT            int"
    String[] parts = line.split("\\s+", 3);
    if (parts.length >= 3) {
      try {
        int index = Integer.parseInt(parts[0]);
        String lexicalUnit = parts[2].trim();
        symbolTable.put(index, lexicalUnit);
      } catch (NumberFormatException e) {
        // Skip invalid entries
      }
    }
  }
  
  /**
   * Parses a token stream entry: "KR_INT               1       0"
   */
  private Token parseTokenStreamEntry(String line, Map<Integer, String> symbolTable) {
    // Format: "KR_INT               1       0"
    String[] parts = line.split("\\s+");
    if (parts.length >= 3) {
      try {
        String tokenType = parts[0];
        int lineNumber = Integer.parseInt(parts[1]);
        int symbolIndex = Integer.parseInt(parts[2]);
        String lexicalUnit = symbolTable.getOrDefault(symbolIndex, "");
        return new Token(tokenType, lineNumber, lexicalUnit);
      } catch (NumberFormatException e) {
        // Skip invalid entries
      }
    }
    return null;
  }
  
  /**
   * Reads tokens in simplified format: "UNIFORMNI_ZNAK REDAK LEKSIČKA_JEDINKA"
   */
  private List<Token> readSimplifiedFormat(List<String> lines) {
    List<Token> tokens = new ArrayList<>();
    for (String line : lines) {
      line = line.trim();
      if (line.isEmpty()) {
        continue;
      }
      
      // Format: "TOKEN_TYPE LINE_NUMBER LEXICAL_UNIT"
      String[] parts = line.split("\\s+", 3);
      if (parts.length >= 2) {
        try {
          String tokenType = parts[0];
          int lineNumber = Integer.parseInt(parts[1]);
          String lexicalUnit = parts.length > 2 ? parts[2] : "";
          tokens.add(new Token(tokenType, lineNumber, lexicalUnit));
        } catch (NumberFormatException e) {
          // Skip invalid entries
        }
      }
    }
    return tokens;
  }
}

