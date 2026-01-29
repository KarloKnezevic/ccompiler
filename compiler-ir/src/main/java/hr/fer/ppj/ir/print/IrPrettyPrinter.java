package hr.fer.ppj.ir.print;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrGlobalVar;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.model.IrStructDef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pretty printer for IR programs that outputs exactly the grammar format.
 *
 * <p>The output follows the EBNF grammar in config/ir_definition.txt exactly,
 * ensuring deterministic output for golden tests.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrPrettyPrinter {

  private final StringBuilder out = new StringBuilder();
  private final ValuePrinter valuePrinter;
  private final RhsPrinter rhsPrinter;
  private final InstructionPrinter instructionPrinter;
  private final TerminatorPrinter terminatorPrinter;
  private final BlockPrinter blockPrinter;

  private IrPrettyPrinter() {
    this.valuePrinter = new ValuePrinter(out);
    this.rhsPrinter = new RhsPrinter(out, valuePrinter);
    this.instructionPrinter = new InstructionPrinter(out, rhsPrinter, valuePrinter);
    this.terminatorPrinter = new TerminatorPrinter(out, valuePrinter);
    this.blockPrinter = new BlockPrinter(out, instructionPrinter, terminatorPrinter);
  }

  /**
   * Prints an IR program to a string.
   */
  public static String print(IrProgram program) {
    IrPrettyPrinter printer = new IrPrettyPrinter();
    printer.printProgram(program);
    return printer.out.toString();
  }

  private void printProgram(IrProgram program) {
    out.append(".program\n");
    
    // Add blank line after .program (matches golden files)
    out.append("\n");

    // Print globals (sorted by name for determinism)
    List<IrGlobalVar> globals = new ArrayList<>(program.globals());
    globals.sort(Comparator.comparing(IrGlobalVar::name));
    if (!globals.isEmpty()) {
      out.append(".globals\n");
      for (IrGlobalVar global : globals) {
        out.append("  global ").append(global.name()).append(":").append(global.type().toIrString());
        if (global.initializer() != null) {
          out.append(" = ").append(global.initializer().toIrString());
        }
        out.append("\n");
      }
      out.append("\n"); // Blank line after globals
    }

    // Print struct definitions (sorted by name for determinism)
    List<IrStructDef> structDefs = new ArrayList<>(program.structDefs().values());
    structDefs.sort(Comparator.comparing(IrStructDef::name));
    for (IrStructDef structDef : structDefs) {
      out.append(".type struct ").append(structDef.name()).append(" {\n");
      // Sort fields by name for determinism
      List<Map.Entry<String, IrStructDef.Field>> sortedFields = structDef.fields().entrySet().stream()
          .sorted(Comparator.comparing(Map.Entry::getKey))
          .collect(Collectors.toList());
      for (Map.Entry<String, IrStructDef.Field> entry : sortedFields) {
        String fieldName = entry.getKey();
        IrStructDef.Field field = entry.getValue();
        out.append(fieldName).append(":").append(field.type().toIrString())
            .append("@").append(field.offset()).append("\n");
      }
      out.append("}\n");
    }

    // Print functions (sorted by name for determinism, with blank line between them)
    List<IrFunction> functions = new ArrayList<>(program.functions());
    functions.sort(Comparator.comparing(IrFunction::name));
    for (int i = 0; i < functions.size(); i++) {
      if (i > 0) {
        out.append("\n"); // Exactly one blank line between functions
      }
      printFunction(functions.get(i));
    }
    
    // Add blank line before .endprogram (matches golden files)
    if (!functions.isEmpty()) {
      out.append("\n");
    }
    
    out.append(".endprogram\n");
  }

  private void printFunction(IrFunction function) {
    // Function signature (column 0)
    out.append(".func ").append(function.name()).append("(");
    List<IrFunction.Parameter> params = function.parameters();
    for (int i = 0; i < params.size(); i++) {
      if (i > 0) {
        out.append(", ");
      }
      IrFunction.Parameter param = params.get(i);
      out.append(param.name()).append(":").append(param.type().toIrString());
    }
    out.append("):");
    if (function.returnType() != null) {
      out.append(function.returnType().toIrString());
    } else {
      out.append("void");
    }
    out.append("\n");

    // Frame (indented 2 spaces)
    out.append("  .frame locals=").append(function.localsBytes())
        .append(" bytes align=").append(function.alignBytes()).append("\n");

    // Slots (sorted by offset, then by name for determinism)
    List<IrSlot> slots = new ArrayList<>(function.slots());
    slots.sort(Comparator.comparing(IrSlot::offset).thenComparing(IrSlot::name));
    out.append("  .slots\n");
    for (IrSlot slot : slots) {
      // Slot entries indented 4 spaces
      out.append("    ").append(slot.kind().toIrString()).append(" ").append(slot.name())
          .append("@").append(slot.offset()).append(":").append(slot.type().toIrString()).append("\n");
    }

    // Blocks (sorted by label for determinism)
    List<IrBlock> blocks = new ArrayList<>(function.blocks());
    blocks.sort(Comparator.comparing(IrBlock::label));
    out.append("  .blocks\n");
    for (IrBlock block : blocks) {
      blockPrinter.printBlock(block);
    }

    out.append(".endfunc\n");
  }
}

