package ch.cstettler.thymeleaf;

import org.thymeleaf.context.IExpressionContext;

public class ComponentExpressionObject {

  private final IExpressionContext context;

  ComponentExpressionObject(IExpressionContext context) {
    this.context = context;
  }

  private ReplaceSlotTagProcessor.SlotData getSlotData() {
    if (context.getVariable(ReplaceSlotTagProcessor.SLOTS_DATA) instanceof ReplaceSlotTagProcessor.SlotData slotData) {
      return slotData;
    } else {
      return null;
    }
  }

  public boolean isDefaultSlotDefined() {
    return isSlotDefined (ReplaceSlotTagProcessor.DEFAULT_SLOT_NAME);
  }

  public boolean isSlotDefined(String slotName) {
    ReplaceSlotTagProcessor.SlotData slotData = getSlotData();
    return slotData != null && slotData.contains (slotName);
  }
}
