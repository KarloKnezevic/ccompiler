package hr.fer.ppj.semantics.analysis;

/**
 * Constants used throughout the semantic analysis phase.
 * 
 * <p>This class centralizes all magic strings, numeric constants, and other literals
 * used in semantic rule implementations to improve maintainability and reduce errors.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticConstants {

  // Prevent instantiation
  private SemanticConstants() {}

  // ========== FUNCTION NAMES ==========
  
  /**
   * Name of the required main function according to PPJ-C specification.
   * Every valid program must contain exactly one function with this name,
   * signature {@code int main(void)}, and a definition (not just declaration).
   */
  public static final String MAIN_FUNCTION_NAME = "main";

  // ========== ARRAY LIMITS ==========
  
  /**
   * Maximum allowed array length according to PPJ-C specification.
   * Arrays with length greater than this value are rejected as semantic errors.
   */
  public static final int MAX_ARRAY_LENGTH = 1024;

  // ========== LITERAL VALIDATION ==========
  
  /**
   * Set of valid escape sequences in character and string literals.
   * According to PPJ-C specification: \n, \t, \0, \', \"
   */
  public static final String VALID_ESCAPE_SEQUENCES = "nt0\\'\"";

  // ========== TERMINAL SYMBOLS ==========
  
  /** Left parenthesis terminal symbol */
  public static final String L_ZAGRADA = "L_ZAGRADA";
  
  /** Right parenthesis terminal symbol */
  public static final String D_ZAGRADA = "D_ZAGRADA";
  
  /** Left square bracket terminal symbol */
  public static final String L_UGL_ZAGRADA = "L_UGL_ZAGRADA";
  
  /** Right square bracket terminal symbol */
  public static final String D_UGL_ZAGRADA = "D_UGL_ZAGRADA";
  
  /** Left curly brace terminal symbol */
  public static final String L_VIT_ZAGRADA = "L_VIT_ZAGRADA";
  
  /** Right curly brace terminal symbol */
  public static final String D_VIT_ZAGRADA = "D_VIT_ZAGRADA";

  /** Identifier terminal symbol */
  public static final String IDN = "IDN";
  
  /** Integer literal terminal symbol */
  public static final String BROJ = "BROJ";
  
  /** Character literal terminal symbol */
  public static final String ZNAK = "ZNAK";
  
  /** String literal terminal symbol */
  public static final String NIZ_ZNAKOVA = "NIZ_ZNAKOVA";

  /** Assignment operator terminal symbol */
  public static final String OP_PRIDRUZI = "OP_PRIDRUZI";
  
  /** Prefix/postfix increment operator terminal symbol */
  public static final String OP_INC = "OP_INC";
  
  /** Prefix/postfix decrement operator terminal symbol */
  public static final String OP_DEC = "OP_DEC";

  /** Plus operator terminal symbol */
  public static final String PLUS = "PLUS";
  
  /** Minus operator terminal symbol */
  public static final String MINUS = "MINUS";
  
  /** Bitwise NOT operator terminal symbol */
  public static final String OP_TILDA = "OP_TILDA";
  
  /** Logical NOT operator terminal symbol */
  public static final String OP_NEG = "OP_NEG";
  
  /** Address-of operator terminal symbol */
  public static final String AMPERSAND = "AMPERSAND";
  
  /** Dereference operator terminal symbol */
  public static final String ASTERISK = "ASTERISK";
  
  /** Struct member access operator terminal symbol */
  public static final String TOCKA = "TOCKA";

  // ========== KEYWORD SYMBOLS ==========
  
  /** void keyword terminal symbol */
  public static final String KR_VOID = "KR_VOID";
  
  /** int keyword terminal symbol */
  public static final String KR_INT = "KR_INT";
  
  /** char keyword terminal symbol */
  public static final String KR_CHAR = "KR_CHAR";
  
  /** float keyword terminal symbol */
  public static final String KR_FLOAT = "KR_FLOAT";
  
  /** struct keyword terminal symbol */
  public static final String KR_STRUCT = "KR_STRUCT";
  
  /** const keyword terminal symbol */
  public static final String KR_CONST = "KR_CONST";

  /** if keyword terminal symbol */
  public static final String KR_IF = "KR_IF";
  
  /** else keyword terminal symbol */
  public static final String KR_ELSE = "KR_ELSE";
  
  /** while keyword terminal symbol */
  public static final String KR_WHILE = "KR_WHILE";
  
  /** for keyword terminal symbol */
  public static final String KR_FOR = "KR_FOR";

  /** break keyword terminal symbol */
  public static final String KR_BREAK = "KR_BREAK";
  
  /** continue keyword terminal symbol */
  public static final String KR_CONTINUE = "KR_CONTINUE";
  
  /** return keyword terminal symbol */
  public static final String KR_RETURN = "KR_RETURN";

  /** Semicolon terminal symbol */
  public static final String TOCKAZAREZ = "TOCKAZAREZ";
  
  /** Comma terminal symbol */
  public static final String ZAREZ = "ZAREZ";

  // ========== NON-TERMINAL SYMBOLS ==========
  
  /** Translation unit non-terminal */
  public static final String PRIJEVODNA_JEDINICA = "<prijevodna_jedinica>";
  
  /** External declaration non-terminal */
  public static final String VANJSKA_DEKLARACIJA = "<vanjska_deklaracija>";
  
  /** Function definition non-terminal */
  public static final String DEFINICIJA_FUNKCIJE = "<definicija_funkcije>";

  /** Primary expression non-terminal */
  public static final String PRIMARNI_IZRAZ = "<primarni_izraz>";
  
  /** Postfix expression non-terminal */
  public static final String POSTFIKS_IZRAZ = "<postfiks_izraz>";
  
  /** Assignment expression non-terminal */
  public static final String IZRAZ_PRIDRUZIVANJA = "<izraz_pridruzivanja>";

  /** Compound statement non-terminal */
  public static final String SLOZENA_NAREDBA = "<slozena_naredba>";
  
  /** Jump statement non-terminal */
  public static final String NAREDBA_SKOKA = "<naredba_skoka>";

  // ========== ERROR MESSAGES ==========
  
  /** Error message for missing main function */
  public static final String ERROR_MISSING_MAIN = "main";
  
  /** Error message for undefined function */
  public static final String ERROR_UNDEFINED_FUNCTION = "funkcija";
}
