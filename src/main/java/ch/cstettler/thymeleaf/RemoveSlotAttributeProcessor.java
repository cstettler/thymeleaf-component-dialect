package ch.cstettler.thymeleaf;

import static java.lang.Integer.MAX_VALUE;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.AttributeName;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractAttributeTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;

class RemoveSlotAttributeProcessor extends AbstractAttributeTagProcessor {

  RemoveSlotAttributeProcessor(String dialectPrefix, String attributeName) {
    super(HTML, dialectPrefix, null, false, attributeName, true, MAX_VALUE, true);
  }

  @Override
  protected void doProcess(
    ITemplateContext context,
    IProcessableElementTag tag,
    AttributeName attributeName,
    String attributeValue,
    IElementTagStructureHandler structureHandler
  ) {
    // do nothing, just removes pl:slot attributes from slot content to avoid html markup pollution
  }
}
