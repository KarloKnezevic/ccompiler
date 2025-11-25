package hr.fer.ppj.semantics.tree;

import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
}

