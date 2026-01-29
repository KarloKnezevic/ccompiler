package hr.fer.ppj.semantics.tree;

import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Mutable container for semantic attributes attached to non-terminal nodes.
 *
 * <p>Attributes are intentionally lightweight and mutable because the semantic checker
 * populates them while traversing the parse tree.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticAttributes {

  private Type type;
  private boolean lValue;
  private boolean constValue;
  private FunctionType functionType;
  private final List<Type> parameterTypes = new ArrayList<>();
  private final List<String> parameterNames = new ArrayList<>();
  private final List<Type> initializerElementTypes = new ArrayList<>();
  private boolean containsReturn;
  private Type inheritedType;
  private int elementCount;
  private int initializerElementCount;
  private String identifier;
  private boolean stringLiteral;
  private int stringLiteralLength;
  private Map<String, Type> structFields;
  private Type castSourceType;
  private CastCategory castCategory;

  public Type type() {
    return type;
  }

  public SemanticAttributes type(Type type) {
    this.type = type;
    return this;
  }

  public boolean isLValue() {
    return lValue;
  }

  public SemanticAttributes lValue(boolean lValue) {
    this.lValue = lValue;
    return this;
  }

  public boolean isConstValue() {
    return constValue;
  }

  public SemanticAttributes constValue(boolean constValue) {
    this.constValue = constValue;
    return this;
  }

  public FunctionType functionType() {
    return functionType;
  }

  public SemanticAttributes functionType(FunctionType functionType) {
    this.functionType = functionType;
    return this;
  }

  public List<Type> parameterTypes() {
    return Collections.unmodifiableList(parameterTypes);
  }

  public SemanticAttributes parameterTypes(List<Type> types) {
    Objects.requireNonNull(types, "types must not be null");
    parameterTypes.clear();
    parameterTypes.addAll(types);
    return this;
  }

  public List<String> parameterNames() {
    return Collections.unmodifiableList(parameterNames);
  }

  public SemanticAttributes parameterNames(List<String> names) {
    Objects.requireNonNull(names, "names must not be null");
    parameterNames.clear();
    parameterNames.addAll(names);
    return this;
  }

  public boolean containsReturn() {
    return containsReturn;
  }

  public SemanticAttributes containsReturn(boolean containsReturn) {
    this.containsReturn = containsReturn;
    return this;
  }

  public Type inheritedType() {
    return inheritedType;
  }

  public SemanticAttributes inheritedType(Type inheritedType) {
    this.inheritedType = inheritedType;
    return this;
  }

  public int elementCount() {
    return elementCount;
  }

  public SemanticAttributes elementCount(int elementCount) {
    this.elementCount = elementCount;
    return this;
  }

  public int initializerElementCount() {
    return initializerElementCount;
  }

  public SemanticAttributes initializerElementCount(int initializerElementCount) {
    this.initializerElementCount = initializerElementCount;
    return this;
  }

  public List<Type> initializerElementTypes() {
    return Collections.unmodifiableList(initializerElementTypes);
  }

  public SemanticAttributes initializerElementTypes(List<Type> types) {
    Objects.requireNonNull(types, "types must not be null");
    initializerElementTypes.clear();
    initializerElementTypes.addAll(types);
    return this;
  }

  public void reset() {
    type = null;
    lValue = false;
    constValue = false;
    functionType = null;
    parameterTypes.clear();
    parameterNames.clear();
    initializerElementTypes.clear();
    containsReturn = false;
    inheritedType = null;
    elementCount = 0;
    initializerElementCount = 0;
    identifier = null;
    stringLiteral = false;
    stringLiteralLength = 0;
    structFields = null;
  }

  public String identifier() {
    return identifier;
  }

  public SemanticAttributes identifier(String identifier) {
    this.identifier = identifier;
    return this;
  }

  public boolean isStringLiteral() {
    return stringLiteral;
  }

  public SemanticAttributes stringLiteral(boolean stringLiteral) {
    this.stringLiteral = stringLiteral;
    return this;
  }

  public int stringLiteralLength() {
    return stringLiteralLength;
  }

  public SemanticAttributes stringLiteralLength(int stringLiteralLength) {
    this.stringLiteralLength = stringLiteralLength;
    return this;
  }

  public Map<String, Type> structFields() {
    return structFields == null ? null : Collections.unmodifiableMap(structFields);
  }

  public SemanticAttributes structFields(Map<String, Type> fields) {
    if (fields == null) {
      this.structFields = null;
    } else {
      this.structFields = new LinkedHashMap<>(fields);
    }
    return this;
  }

  public Type castSourceType() {
    return castSourceType;
  }

  public SemanticAttributes castSourceType(Type castSourceType) {
    this.castSourceType = castSourceType;
    return this;
  }

  public CastCategory castCategory() {
    return castCategory;
  }

  public SemanticAttributes castCategory(CastCategory castCategory) {
    this.castCategory = castCategory;
    return this;
  }

  /**
   * Enumeration of cast categories for IR generation.
   *
   * <p>These categories determine which IR cast operation to use:
   * <ul>
   *   <li>{@code TRUNC}: Truncate (int32 → char, int32 → bool)</li>
   *   <li>{@code SEXT}: Sign extend (char → int32, since char is signed)</li>
   *   <li>{@code ZEXT}: Zero extend (not used in this subset, char is signed)</li>
   *   <li>{@code PTRCAST}: Pointer cast (ptr<T> → ptr<U>)</li>
   *   <li>{@code ITOF}: Integer to float (int32 → float, char → float)</li>
   *   <li>{@code FTOI}: Float to integer (float → int32, float → char)</li>
   * </ul>
   */
  public enum CastCategory {
    /** Truncate: int32 → char, int32 → bool */
    TRUNC,
    /** Sign extend: char → int32 */
    SEXT,
    /** Zero extend: not used (char is signed) */
    ZEXT,
    /** Pointer cast: ptr<T> → ptr<U> */
    PTRCAST,
    /** Integer to float: int32 → float, char → float */
    ITOF,
    /** Float to integer: float → int32, float → char */
    FTOI
  }
}

