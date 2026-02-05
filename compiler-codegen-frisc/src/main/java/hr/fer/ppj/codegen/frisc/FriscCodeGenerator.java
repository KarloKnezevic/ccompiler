package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.frisc.analysis.PointerScratchCollector;
import hr.fer.ppj.codegen.frisc.analysis.TempAnalyzer;
import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.frame.ParamLayoutBuilder;
import hr.fer.ppj.codegen.frisc.helpers.HelperLibrary;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import hr.fer.ppj.codegen.frisc.lowering.AddressLowerer;
import hr.fer.ppj.codegen.frisc.lowering.ExpressionLowerer;
import hr.fer.ppj.codegen.frisc.lowering.FrameAccess;
import hr.fer.ppj.codegen.frisc.lowering.ImmediateEmitter;
import hr.fer.ppj.codegen.frisc.lowering.StatementLowerer;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Entry point for FRISC code generation from typed IR text.
 */
public final class FriscCodeGenerator {

  static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final IrTextParser parser;
  private final ProgramEmitter programEmitter;

  public FriscCodeGenerator() {
    this.parser = new IrTextParser();

    LabelGenerator labelGenerator = new LabelGenerator();
    FrameAccess frameAccess = new FrameAccess();
    ImmediateEmitter immediateEmitter = new ImmediateEmitter();
    AddressLowerer addressLowerer = new AddressLowerer(labelGenerator, immediateEmitter);
    ExpressionLowerer expressionLowerer = new ExpressionLowerer(
        labelGenerator, frameAccess, addressLowerer, immediateEmitter, labelGenerator::functionLabel);
    addressLowerer.bindValueEmitter(expressionLowerer::emitValue);
    StatementLowerer statementLowerer = new StatementLowerer(expressionLowerer, frameAccess, addressLowerer);

    TempAnalyzer tempAnalyzer = new TempAnalyzer();
    FunctionEmitter functionEmitter = new FunctionEmitter(
        labelGenerator, immediateEmitter, addressLowerer, statementLowerer, tempAnalyzer);
    GlobalsEmitter globalsEmitter = new GlobalsEmitter(labelGenerator);
    HelperEmitter helperEmitter = new HelperEmitter(new HelperLibrary());
    PointerScratchCollector pointerScratchCollector = new PointerScratchCollector();
    ParamLayoutBuilder paramLayoutBuilder = new ParamLayoutBuilder();

    this.programEmitter = new ProgramEmitter(
        labelGenerator,
        functionEmitter,
        globalsEmitter,
        helperEmitter,
        pointerScratchCollector,
        paramLayoutBuilder);
  }

  /**
   * Generates FRISC assembly for the provided IR text and writes it to a file.
   */
  public void generate(String irText, Path outputFile, String sourceName) {
    Objects.requireNonNull(irText, "irText must not be null");
    Objects.requireNonNull(outputFile, "outputFile must not be null");
    IrProgramModel program = parser.parse(irText);
    FriscEmitter emitter = new FriscEmitter();
    programEmitter.emit(program, emitter, sourceName);
    emitter.writeToFile(outputFile);
  }
}
