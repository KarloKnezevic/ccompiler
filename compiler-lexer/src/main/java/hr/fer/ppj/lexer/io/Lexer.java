package hr.fer.ppj.lexer.io;

import hr.fer.ppj.lexer.dfa.DFA;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.state.LexerState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Lexical analyzer (lexer) that tokenizes input text using generated deterministic finite automata (DFAs).
 * 
 * <p>This lexer implements the following algorithms and mechanisms:
 * 
 * <ul>
 *   <li><strong>Algorithm B - Maximal Munch (P2):</strong> Always selects the longest possible match.
 *       If multiple rules match the same length, rule order (P3) is used to break ties.</li>
 *   <li><strong>Algorithm B - Rule Order (P3):</strong> Earlier rules in the specification have
 *       priority over later rules that match the same length.</li>
 *   <li><strong>Algorithm C - Error Recovery:</strong> If an unrecognized character is encountered,
 *       the first character is discarded and analysis continues (panic mode recovery).</li>
 * </ul>
 * 
 * <p><strong>Supported Actions:</strong>
 * <ul>
 *   <li><strong>UDJI_U_STANJE &lt;state&gt;</strong>: Changes the current lexer state.
 *       Used for comments, string literals, etc. This allows the lexer to switch between
 *       different sets of matching rules based on context.</li>
 *   <li><strong>VRATI_SE &lt;n&gt;</strong> (equivalent to yyless(n)): Returns characters to the input stream.
 *       Of the matched characters, the first n characters are grouped into the lexical token,
 *       while the remaining characters are returned to the input buffer for the next tokenization step.</li>
 *   <li><strong>NOVI_REDAK</strong>: Increments the line number for the next lexical token.
 *       This is used when a newline character is encountered or when explicitly triggered by an action.</li>
 * </ul>
 * 
 * <p><strong>Symbol Table:</strong>
 * Every token is automatically added to the symbol table. Tokens with the same type and text
 * share the same index in the symbol table, enabling efficient storage and lookup.
 * 
 * <p><strong>Token Positions:</strong>
 * Each token contains information about the line and column where it was found.
 * The line number is incremented when a newline character ('\n') is encountered or when
 * the NOVI_REDAK action is executed.
 * 
 * <p><strong>Lexer States:</strong>
 * The lexer maintains a current state (e.g., S_pocetno, S_string, S_komentar) that determines
 * which set of DFA rules are active. State transitions occur when UDJI_U_STANJE actions are executed.
 * 
 * <p><strong>Error Handling:</strong>
 * <ul>
 *   <li>Unrecognized characters trigger error recovery (Algorithm C)</li>
 *   <li>Unterminated string literals are detected and reported with proper error messages</li>
 *   <li>Error recovery ensures the lexer can continue processing after encountering errors</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @version 1.0
 */
public final class Lexer {
  
  /** 
   * Result of lexer generation - contains DFA for each lexer state.
   * Each state has its own DFA that defines which patterns can be matched in that state.
   */
  private final LexerGeneratorResult generatorResult;
  
  /**
   * Symbol table - list of all unique tokens (type + text pairs).
   * Tokens with the same type and text share the same index, enabling efficient storage.
   */
  private final List<SymbolTableEntry> symbolTableList = new ArrayList<>();
  
  /**
   * Entry in the symbol table - a pair of (token type, source text).
   * 
   * <p>This record represents a single entry in the symbol table. Multiple tokens
   * with the same type and text will reference the same symbol table entry.
   * 
   * @param token Token type (uniform character) - e.g., "IDN", "BROJ", "KR_INT"
   * @param text Source text of the token - e.g., "x", "42", "int"
   */
  public record SymbolTableEntry(String token, String text) {}
  
  /**
   * Constructs a new lexer with the given generator result.
   * 
   * <p>The generator result contains all the DFAs for each lexer state, which are
   * used during tokenization to match patterns and generate tokens.
   * 
   * @param generatorResult Result of lexer generation containing DFAs for each state
   */
  public Lexer(LexerGeneratorResult generatorResult) {
    this.generatorResult = generatorResult;
  }
  
  /**
   * Tokenizes the input text and returns a list of tokens.
   * 
   * <p><strong>Tokenization Algorithm:</strong>
   * <ol>
   *   <li>Read input text line by line and add it to the buffer</li>
   *   <li>For each line, attempt to find the longest match using the DFA for the current state</li>
   *   <li>If a match is found, create a token and add it to the list (unless it's whitespace/comment)</li>
   *   <li>If no match is found, check if the state changed (VRATI_SE 0 with UDJI_U_STANJE)</li>
   *   <li>If nothing happened, apply Algorithm C - discard the first character and print an error</li>
   * </ol>
   * 
   * <p><strong>Maximal Munch (P2):</strong>
   * The lexer always selects the longest possible match. If multiple rules match
   * the same length, rule order (P3) is used - earlier rules have priority.
   * 
   * <p><strong>Error Recovery (Algorithm C):</strong>
   * If an unrecognized character is encountered (a character for which there is no transition in the DFA),
   * the first character is discarded from the buffer, an error is printed to stderr, and analysis continues.
   * 
   * <p><strong>State Management:</strong>
   * The lexer maintains a current state that determines which DFA is used for matching.
   * State transitions occur when UDJI_U_STANJE actions are executed.
   * 
   * <p><strong>Special Handling:</strong>
   * <ul>
   *   <li>String literals: Stops matching at the closing quote to prevent over-matching</li>
   *   <li>Unterminated strings: Detects and reports errors, then recovers by exiting the string state</li>
   *   <li>Comments: Whitespace and comments are skipped (no tokens generated)</li>
   * </ul>
   * 
   * @param input Reader that reads the input text
   * @return List of tokens found in the input text
   * @throws IOException if an error occurs while reading the input
   */
  public List<Token> tokenize(Reader input) throws IOException {
    List<Token> tokens = new ArrayList<>();
    BufferedReader reader = new BufferedReader(input);
    
    // Initialize state to the starting state (S_pocetno)
    LexerState state = new LexerState("S_pocetno");
    StringBuilder buffer = new StringBuilder();
    String line;
    
    // Read line by line while there is data
    // Continue processing buffer even when there are no more lines (to process remaining content)
    boolean reading = true;
    while (reading || buffer.length() > 0) {
      if (reading) {
        line = reader.readLine();
        if (line != null) {
          // Add line to buffer with newline at the end
          buffer.append(line).append('\n');
        } else {
          // No more lines to read
          reading = false;
        }
      }
      
      // Safety limit to prevent infinite loops
      int iterations = 0;
      int maxIterations = 100000;
      
      // Process buffer while there is data
      while (buffer.length() > 0 && iterations < maxIterations) {
        iterations++;
        
        // Save state before attempting to match (for detecting state changes)
        int beforeLength = buffer.length();
        String beforeState = state.getCurrentState();
        
        // Pokušaj pronaći token u bufferu
        Token token = scanToken(buffer, state);
        
        // Provjeri stanje nakon pokušaja matchanja
        int afterLength = buffer.length();
        String afterState = state.getCurrentState();
        
        if (token != null) {
          // Token je pronađen - dodaj ga u listu samo ako ima tip
          // (preskačemo whitespace/komentare koji vraćaju null token)
          if (token.type() != null && !token.type().isEmpty()) {
            tokens.add(token);
          }
        } else {
          // Token nije pronađen - provjeri je li se nešto dogodilo
          // (buffer se smanjio ILI stanje se promijenilo)
          
          // Promjena stanja (npr. VRATI_SE 0 s UDJI_U_STANJE) znači da treba nastaviti
          if (afterLength < beforeLength || !beforeState.equals(afterState)) {
            // Nešto je potrošeno (whitespace/komentar) ili stanje se promijenilo (VRATI_SE 0)
            // Nastavi s sljedećom iteracijom
            continue;
          }
          
          // Provjeri je li problem s nezatvorenim stringom
          if (state.getCurrentState().equals("S_string") && buffer.length() > 0) {
            // U stanju S_string, ali nema matcha - provjeri je li nezatvoren string
            char firstChar = buffer.charAt(0);
            
            // Provjeri je li prvi znak novi redak ili je buffer prazan (EOF)
            if (firstChar == '\n') {
              // Nezatvoren string literal - javi grešku na početku stringa
              // Pronađi početak stringa - traži unazad do prvog " ili početka buffera
              int stringStart = -1;
              for (int j = buffer.length() - 1; j >= 0; j--) {
                if (buffer.charAt(j) == '"') {
                  stringStart = j;
                  break;
                }
              }
              
              // Ako nismo našli ", string počinje na početku buffera
              if (stringStart == -1) {
                stringStart = 0;
              }
              
              // Izračunaj poziciju greške (početak stringa)
              // Pozicija greške je na početku stringa (gdje je otvoreni ")
              int errorLine = state.getLineNumber();
              int errorCol = state.getColumnNumber() - buffer.length() + stringStart;
              // Osiguraj da je stupac pozitivan
              if (errorCol < 1) {
                errorCol = 1;
              }
              
              System.err.println(String.format(
                  "Leksička greška na retku %d, stupcu %d: nezatvoren string literal", 
                  errorLine, errorCol));
              
              // Izbriši sve do novog retka (uključujući novi redak)
              // Ovo uklanja nezatvoreni string iz buffera
              int newlineIndex = buffer.indexOf("\n");
              if (newlineIndex >= 0) {
                buffer.delete(0, newlineIndex + 1);
              } else {
                // Nema novog retka - izbriši sve
                buffer.setLength(0);
              }
              
              // Izađi iz stanja S_string i vrati se u S_pocetno
              state.enterState("S_pocetno");
              state.newLine();
              
              // Nastavi s tokenizacijom
              continue;
            } else {
              // U S_string stanju, ali znak nije matchan i nije novi redak
              // To znači da je string nezatvoren (nema završnog ")
              // Pronađi početak stringa i odbaci sve do novog retka ili do kraja buffera
              
              // Pronađi početak stringa - traži unazad do prvog " ili početka buffera
              int stringStart = -1;
              for (int j = buffer.length() - 1; j >= 0; j--) {
                if (buffer.charAt(j) == '"') {
                  stringStart = j;
                  break;
                }
              }
              
              // Ako nismo našli ", string počinje na početku buffera
              if (stringStart == -1) {
                stringStart = 0;
              }
              
              // Izračunaj poziciju greške (početak stringa)
              // Pozicija greške je na početku stringa (gdje je otvoreni ")
              int errorLine = state.getLineNumber();
              int errorCol = state.getColumnNumber() - buffer.length() + stringStart;
              // Osiguraj da je stupac pozitivan
              if (errorCol < 1) {
                errorCol = 1;
              }
              
              System.err.println(String.format(
                  "Leksička greška na retku %d, stupcu %d: nezatvoren string literal", 
                  errorLine, errorCol));
              
              // Pronađi novi redak ili kraj buffera
              int newlineIndex = buffer.indexOf("\n");
              if (newlineIndex >= 0) {
                // Izbriši sve do novog retka (uključujući novi redak)
                buffer.delete(0, newlineIndex + 1);
                // Izađi iz stanja S_string i vrati se u S_pocetno
                state.enterState("S_pocetno");
                state.newLine();
              } else {
                // Nema novog retka - izbriši sve (EOF)
                buffer.setLength(0);
                // Izađi iz stanja S_string i vrati se u S_pocetno
                state.enterState("S_pocetno");
              }
              
              // Nastavi s tokenizacijom
              continue;
            }
          }
          
          // Nije pronađen match i ništa se nije dogodilo
          // Primjeni Algoritam C: Oporavak od leksičke pogreške - odbaci prvi znak
          if (buffer.length() > 0) {
            char c = buffer.charAt(0);
            int errorLine = state.getLineNumber();
            int errorCol = state.getColumnNumber();
            
            // Ispiši grešku na stderr prema uputi
            System.err.println(String.format(
                "Leksička greška na retku %d, stupcu %d: neprepoznat znak '%c' (0x%02x)", 
                errorLine, errorCol, c, (int)c));
            
            // Odstrani prvi znak iz buffera
            buffer.deleteCharAt(0);
            
            // Ažuriraj poziciju (broj retka ili stupca)
            if (c == '\n') {
              state.newLine();
            } else {
              state.advanceColumn();
            }
          } else if (buffer.length() == 0) {
            // Buffer je prazan - provjeri je li nezatvoren string
            if (state.getCurrentState().equals("S_string")) {
              // Pronađi početak stringa u nedavno pročitanom tekstu
              // Za sada, koristimo trenutnu poziciju
              int errorLine = state.getLineNumber();
              int errorCol = state.getColumnNumber();
              System.err.println(String.format(
                  "Leksička greška na retku %d, stupcu %d: nezatvoren string literal", 
                  errorLine, errorCol));
              // Izađi iz stanja S_string
              state.enterState("S_pocetno");
            }
            // Buffer je prazan - izađi iz petlje
            break;
          }
        }
      }
      
      // Provjeri je li došlo do beskonačne petlje
      if (iterations >= maxIterations) {
        System.err.println("Warning: Lexer loop exceeded maximum iterations, possible infinite loop");
      }
    }
    
    return tokens;
  }
  
  /**
   * Scans the input buffer for a token using the DFA for the current lexer state.
   * 
   * <p>This method implements the maximal munch algorithm (P2) to find the longest
   * possible match. It traverses the DFA character by character, tracking the longest
   * accepting state encountered. When no more transitions are available, it returns
   * the token associated with the longest match.
   * 
   * <p><strong>Algorithm:</strong>
   * <ol>
   *   <li>Start at the DFA's start state</li>
   *   <li>For each character in the input buffer, follow transitions in the DFA</li>
   *   <li>Track the longest accepting state encountered</li>
   *   <li>When no transition is available, stop and return the longest match</li>
   *   <li>Handle special cases (e.g., string literals stopping at closing quote)</li>
   * </ol>
   * 
   * <p><strong>Special Handling:</strong>
   * <ul>
   *   <li>String literals: Stops matching at the closing quote to prevent over-matching</li>
   *   <li>Escaped quotes: Continues matching if the quote is escaped (part of string content)</li>
   * </ul>
   * 
   * @param input Input buffer containing characters to scan
   * @param lexerState Current lexer state (determines which DFA to use)
   * @return Token if a match is found, null otherwise
   */
  private Token scanToken(StringBuilder input, LexerState lexerState) {
    // Get DFA for current lexer state
    DFA dfa = generatorResult.stateDFAs().get(lexerState.getCurrentState());
    if (dfa == null) {
      // State not found - this shouldn't happen, but handle gracefully
      System.err.println("Warning: DFA not found for state: " + lexerState.getCurrentState());
      return null;
    }
    
    // Empty input - no token to match
    if (input.length() == 0) {
      return null;
    }
    
    int longestMatch = 0;
    String longestToken = null;
    List<String> longestActions = null;
    int currentState = dfa.getStartState();
    int matchLength = 0;
    
    // Maximal munch: find longest match
    // Track the last accepting state we encountered
    int lastAcceptingState = -1;
    int lastAcceptingMatch = 0;
    String lastAcceptingToken = null;
    List<String> lastAcceptingActions = null;
    
    for (int i = 0; i < input.length(); i++) {
      char c = input.charAt(i);
      Integer nextState = dfa.getTransition(currentState, c);
      
      if (nextState == null) {
        // No transition for this character - cannot continue matching
        // If we had an accepting state, use it
        if (lastAcceptingState != -1) {
          longestMatch = lastAcceptingMatch;
          longestToken = lastAcceptingToken;
          longestActions = lastAcceptingActions;
        }
        break;
      }
      
      currentState = nextState;
      matchLength++;
      
      // Check if current state is accepting - if so, update longest match
      if (dfa.isAccepting(currentState)) {
        longestMatch = matchLength;
        longestToken = dfa.getToken(currentState);
        longestActions = dfa.getActions(currentState);
        
        // Store this as the last accepting state in case we hit a dead end
        lastAcceptingState = currentState;
        lastAcceptingMatch = matchLength;
        lastAcceptingToken = longestToken;
        lastAcceptingActions = longestActions;
        
        // IMPORTANT: For string literals, if we just matched a closing quote,
        // we should stop matching here to prevent matching characters after the quote.
        // This is especially important for patterns like "a"+"b" where we want
        // to stop at the closing quote of "a" and not continue matching +"b".
        // BUT: We need to check if this is an escaped quote (\") or a real closing quote.
        // If the previous character was a backslash, this is an escaped quote, not a closing quote.
        if (longestToken != null && longestToken.equals("NIZ_ZNAKOVA") && 
            lexerState.getCurrentState().equals("S_string") && c == '"') {
          // Check if this is an escaped quote (previous char was \)
          boolean isEscaped = (i > 0 && input.charAt(i - 1) == '\\');
          if (!isEscaped) {
            // We just matched the closing quote - stop matching here
            // The next character (if any) should be tokenized separately
            break;
          }
          // If it's escaped, continue matching (it's part of the string content)
        }
      }
    }
    
    // If no match was found, return null
    if (longestMatch == 0) {
      return null;
    }
    
    // Extract the matched text
    String matchedText = input.substring(0, longestMatch);
    
    // Process actions to determine if backtracking is needed
    // VRATI_SE action determines how many characters are kept in the token,
    // and how many are returned to the input stream
    Integer backtrack = null; // null means there is no VRATI_SE action
    if (longestActions != null) {
      for (String action : longestActions) {
        if (action.startsWith("VRATI_SE")) {
          // Parse VRATI_SE action: "VRATI_SE <n>"
          String[] parts = action.split("\\s+");
          if (parts.length > 1) {
            try {
              backtrack = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
              backtrack = null; // Invalid value - treat as no VRATI_SE
            }
          }
        }
      }
    }
    
    // Adjust match length based on backtrack action
    // VRATI_SE n means: keep first n characters, return the rest to input stream
    // 
    // According to specification: "of the read characters, group the first n characters
    // into the lexical token, and return the remaining characters to the input stream"
    // 
    // Examples:
    // - VRATI_SE 0: group 0 characters, return all (character " remains in buffer for next match)
    // - VRATI_SE n (n > 0): group first n characters, return the rest
    int actualMatch;
    if (backtrack != null && backtrack >= 0 && backtrack <= longestMatch) {
      // VRATI_SE n: keep first n characters, return the rest
      actualMatch = backtrack;
    } else {
      // No VRATI_SE action or invalid value - keep entire match
      actualMatch = longestMatch;
    }
    
    // Remove only the actually consumed characters from the input buffer
    // Characters from actualMatch to longestMatch remain in the buffer (they're already there)
    input.delete(0, actualMatch);
    
    // Save line/column number BEFORE processing the match
    // According to specification: "The incremented line number applies only to the next lexical token"
    // This means the line number for the current token is taken BEFORE incrementing
    int savedLine = lexerState.getLineNumber();
    int savedCol = lexerState.getColumnNumber();
    
    // Update line/column number for the matched portion (only for actually consumed characters)
    // Iterate through actually consumed characters and update position
    for (int i = 0; i < actualMatch; i++) {
      if (matchedText.charAt(i) == '\n') {
        // Newline - increment line number and reset column
        lexerState.newLine();
      } else {
        // Regular character - increment column
        lexerState.advanceColumn();
      }
    }
    
    // Execute actions AFTER updating position
    // Actions are executed after position is updated, because they affect
    // the next lexical token, not the current one
    if (longestActions != null) {
      for (String action : longestActions) {
        if (action.startsWith("UDJI_U_STANJE")) {
          // UDJI_U_STANJE <state>: Changes the current lexer state
          // Used for comments, string literals, etc.
          String[] parts = action.split("\\s+");
          if (parts.length > 1) {
            lexerState.enterState(parts[1]);
          }
        } else if (action.equals("NOVI_REDAK")) {
          // NOVI_REDAK: Increments line number for the next token
          // If \n was already found in matchedText, we already incremented in the loop above
          // If it wasn't found, increment now (but this will affect the next token)
          boolean foundNewline = false;
          for (int i = 0; i < actualMatch; i++) {
            if (matchedText.charAt(i) == '\n') {
              foundNewline = true;
              break;
            }
          }
          if (!foundNewline) {
            lexerState.newLine();
          }
        }
        // VRATI_SE is already processed above
      }
    }
    
    // If token is null or empty (whitespace/comment), return null to skip
    // Also check if token is "-" which means skip this match
    if (longestToken == null || longestToken.isEmpty() || longestToken.equals("-")) {
      return null;
    }
    
    // If actualMatch is 0 (VRATI_SE 0), we still need to process state change,
    // but we don't create a token (token will be created in the next match)
    if (actualMatch <= 0) {
      // State change has already occurred, but there's no token to return
      return null;
    }
    
    // Extract the actual token text (only the consumed portion)
    String actualText = matchedText.substring(0, actualMatch);
    
    // Add to symbol table (all tokens go into the symbol table)
    int symbolIndex = getOrAddSymbol(longestToken, actualText);
    
    // Return token with position information and index in symbol table
    return Token.withIndex(
        longestToken,
        actualText,
        savedLine,
        savedCol,
        symbolIndex);
  }
  
  /**
   * Dohvaća ili dodaje unos u tablicu simbola.
   * 
   * <p>Tablica simbola sadrži sve jedinstvene kombinacije (tip tokena, tekst tokena).
   * Ako kombinacija već postoji, vraća se postojeći indeks. Inače se dodaje novi unos.
   * 
   * <p>Ovo osigurava da tokeni s istim tipom i tekstom dijele isti indeks u tablici simbola,
   * što je korisno za optimizaciju i semantičku analizu.
   * 
   * @param token tip tokena (uniformni znak)
   * @param text tekst tokena (izvorni tekst)
   * @return indeks unosa u tablici simbola
   */
  private int getOrAddSymbol(String token, String text) {
    // Provjeri postoji li već ova točna kombinacija token+tekst
    for (int i = 0; i < symbolTableList.size(); i++) {
      SymbolTableEntry entry = symbolTableList.get(i);
      if (entry.token().equals(token) && entry.text().equals(text)) {
        // Kombinacija već postoji - vrati postojeći indeks
        return i;
      }
    }
    
    // Kombinacija ne postoji - dodaj novi unos
    int index = symbolTableList.size();
    symbolTableList.add(new SymbolTableEntry(token, text));
    return index;
  }
  
  /**
   * Vraća kopiju tablice simbola.
   * 
   * @return lista unosa u tablici simbola (tip tokena, tekst tokena)
   */
  public List<SymbolTableEntry> getSymbolTable() {
    return List.copyOf(symbolTableList);
  }
  
  /**
   * Vraća listu tekstova iz tablice simbola.
   * 
   * @return lista tekstova tokena (izvorni tekstovi)
   */
  public List<String> getSymbolTableTexts() {
    return symbolTableList.stream().map(SymbolTableEntry::text).toList();
  }
}


