package ch.cstettler.thymeleaf;

import java.util.HashSet;
import java.util.Set;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;

public class ComponentDialect extends AbstractProcessorDialect {

  private static final String DIALECT_PREFIX = "pl";

  private final Set<IProcessor> processors;

  public ComponentDialect() {
    super("Thymeleaf UI Component Dialect", DIALECT_PREFIX, 0);

    this.processors = new HashSet<>();
    this.processors.add(new RemoveSlotAttributeProcessor(DIALECT_PREFIX, "slot"));

    addComponent("button");
    addComponent("alert");
    addComponent("collapsible");
  }

  public ComponentDialect addComponent(String elementName) {
    return addComponent(elementName, null);
  }

  public ComponentDialect addComponent(String elementName, String templatePath) {
    processors.add(new ComponentModelProcessor(DIALECT_PREFIX, elementName, templatePath));

    return this;
  }

  @Override
  public Set<IProcessor> getProcessors(String dialectPrefix) {
    return processors;
  }
}
