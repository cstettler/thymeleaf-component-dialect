/*
 * Copyright 2025 Christian Stettler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.cstettler.thymeleaf;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.thymeleaf.model.AttributeValueQuotes.DOUBLE;
import static org.thymeleaf.standard.processor.StandardReplaceTagProcessor.PRECEDENCE;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.TemplateManager;
import org.thymeleaf.engine.TemplateModel;
import org.thymeleaf.exceptions.TemplateProcessingException;
import org.thymeleaf.model.IAttribute;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.standard.expression.IStandardExpressionParser;
import org.thymeleaf.standard.expression.StandardExpressions;

class ComponentModelProcessor extends AbstractElementModelProcessor {

  private static final String DEFAULT_SLOT_NAME = ComponentModelProcessor.class.getName() + ".default";

  private final String dialectPrefix;
  private final String elementName;
  private final String templatePath;

  public ComponentModelProcessor(String dialectPrefix, String elementName, String templatePath) {
    super(HTML, dialectPrefix, elementName, true, null, false, PRECEDENCE);

    this.dialectPrefix = dialectPrefix;
    this.elementName = elementName;
    this.templatePath = templatePath != null ? templatePath : dialectPrefix + "/" + elementName + "/" + elementName;
  }

  @Override
  protected void doProcess(ITemplateContext context, IModel model, IElementModelStructureHandler structureHandler) {
    IProcessableElementTag componentElementTag = firstOpenOrStandaloneElementTag(model);

    if (componentElementTag == null) {
      throw new IllegalStateException("no component element tag found in model " + model);
    }

    if (!isValidComponentTag(componentElementTag)) {
      // avoid handling web components named "pl-xyz" (thymeleaf treats "pl-" as prefix the same way as "pl:")
      return;
    }

    IStandardExpressionParser expressionParser = StandardExpressions.getExpressionParser(context.getConfiguration());
    Map<String, Object> additionalAttributes = resolveAdditionalAttributes(componentElementTag, context, expressionParser);
    Map<String, Object> componentAttributes = resolveComponentAttributes(componentElementTag, context, expressionParser);
    componentAttributes.forEach(structureHandler::setLocalVariable);

    IModel fragmentModel = loadFragmentModel(context);
    Map<String, List<ITemplateEvent>> slotContents = extractSlotContents(model);
    Map<String, ITemplateEvent> slots = extractSlots(fragmentModel);
    IModel mergedModel = prepareModel(context, fragmentModel, additionalAttributes, slots, slotContents);

    model.reset();
    model.addModel(mergedModel);
  }

  private boolean isValidComponentTag(IProcessableElementTag componentElementTag) {
    return componentElementTag.getElementCompleteName().startsWith(dialectPrefix + ":");
  }

  private IModel loadFragmentModel(ITemplateContext context) {
    return parseFragmentTemplateModel(context, templatePath);
  }

  private Map<String, List<ITemplateEvent>> extractSlotContents(IModel model) {
    Map<String, List<ITemplateEvent>> slots = new HashMap<>();

    templateEventsIn(model).forEach(templateEvent -> {
      if (isOpenOrStandaloneTag(templateEvent)) {
        IProcessableElementTag elementTag = (IProcessableElementTag) templateEvent;
        if (elementTag.hasAttribute(dialectPrefix, "slot")) {
          String slotName = elementTag.getAttributeValue(dialectPrefix, "slot");

          if (slots.containsKey(slotName)) {
            throw new IllegalStateException("duplicate slot definition '" + slotName + "'");
          }

          slots.put(slotName, subTreeFrom(model, elementTag));
        }
      }
    });

    List<ITemplateEvent> defaultSlotContent = subTreeBelow(model, firstOpenOrStandaloneElementTag(model));
    slots.values().forEach(defaultSlotContent::removeAll);
    slots.put(DEFAULT_SLOT_NAME, defaultSlotContent);

    return slots;
  }

  private Map<String, ITemplateEvent> extractSlots(IModel fragmentModel) {
    Map<String, ITemplateEvent> slots = new HashMap<>();

    templateEventsIn(fragmentModel).forEach(templateEvent -> {
      if (isSlot(templateEvent)) {
        slots.put(slotNameOf((IProcessableElementTag) templateEvent), templateEvent);
      }
    });

    return slots;
  }

  private IModel prepareModel(
    ITemplateContext context,
    IModel fragmentModel,
    Map<String, Object> additionalAttributes,
    Map<String, ITemplateEvent> slots,
    Map<String, List<ITemplateEvent>> slotContents
  ) {
    IModelFactory modelFactory = context.getModelFactory();
    IModel newModel = modelFactory.createModel();

    newModel.add(blockOpenElement(modelFactory, additionalAttributes));

    List<ITemplateEvent> mergedElementTags = fillSlots(fragmentModel, slots, slotContents);
    mergedElementTags.forEach(newModel::add);

    newModel.add(blockCloseElement(modelFactory));

    return newModel;
  }

  private List<ITemplateEvent> fillSlots(
    IModel fragmentModel, Map<String, ITemplateEvent> slots,
    Map<String, List<ITemplateEvent>> slotContents
  ) {
    List<ITemplateEvent> fragmentElementTags = subTreeBelow(fragmentModel, firstOpenElementTagWithAttribute(fragmentModel, "th:fragment"));
    slots.forEach((slotName, slotElementTag) -> {
      List<ITemplateEvent> slotContent = slotContents.get(slotName);

      if (slotContent == null || slotContent.isEmpty()) {
        if (slotElementTag instanceof IOpenElementTag) {
          slotContent = fallbackSlotContent(fragmentModel, (IOpenElementTag) slotElementTag);
        } else {
          slotContent = emptyList();
        }
      }

      fillSlot(fragmentElementTags, subTreeFrom(fragmentModel, slotElementTag), slotContent);
    });

    return fragmentElementTags;
  }

  private void fillSlot(List<ITemplateEvent> templateEvents, List<ITemplateEvent> slotSubTree, List<ITemplateEvent> slotContent) {
    int position = templateEvents.indexOf(slotSubTree.get(0));
    templateEvents.removeAll(slotSubTree);
    templateEvents.addAll(position, slotContent);
  }

  private static List<ITemplateEvent> fallbackSlotContent(IModel fragmentModel, IOpenElementTag slotElementTag) {
    return subTreeBelow(fragmentModel, slotElementTag);
  }

  private static IOpenElementTag blockOpenElement(IModelFactory modelFactory, Map<String, Object> attributes) {
    Map<String, String> attributesMap = new HashMap<>();
    attributes.forEach((key, value) -> attributesMap.put(key, value != null ? value.toString() : null));

    return modelFactory.createOpenElementTag("th:block", attributesMap, DOUBLE, false);
  }

  private static ICloseElementTag blockCloseElement(IModelFactory modelFactory) {
    return modelFactory.createCloseElementTag("th:block");
  }

  private boolean isSlot(ITemplateEvent templateEvent) {
    if (templateEvent instanceof IProcessableElementTag) {
      return ((IProcessableElementTag) templateEvent).getElementCompleteName().equals(dialectPrefix + ":slot");
    }

    return false;
  }

  private boolean isOpenOrStandaloneTag(ITemplateEvent templateEvent) {
    return templateEvent instanceof IProcessableElementTag;
  }

  private String slotNameOf(IProcessableElementTag elementTag) {
    return elementTag.hasAttribute(dialectPrefix, "name")
      ? elementTag.getAttributeValue(dialectPrefix, "name")
      : DEFAULT_SLOT_NAME;
  }

  private static IProcessableElementTag firstOpenOrStandaloneElementTag(IModel model) {
    return templateEventsIn(model).stream()
      .filter((elementTag) -> elementTag instanceof IProcessableElementTag)
      .map(templateEvent -> (IProcessableElementTag)templateEvent)
      .findFirst()
      .orElse(null);
  }

  private static IProcessableElementTag firstOpenElementTagWithAttribute(IModel model, String attributeName) {
    return templateEventsIn(model).stream()
      .filter((elementTag) -> elementTag instanceof IOpenElementTag)
      .map(templateEvent -> (IProcessableElementTag)templateEvent)
      .filter(elementTag -> elementTag.hasAttribute(attributeName))
      .findFirst()
      .orElse(null);
  }

  private Map<String, Object> resolveComponentAttributes(IProcessableElementTag element, ITemplateContext context,
    IStandardExpressionParser expressionParser) {
    Map<String, Object> attributes = new HashMap<>();

    // TODO or use list of predefined attributes per element and read value (potentially null)

    if (element.getAllAttributes() != null) {
      stream(element.getAllAttributes())
        .filter(attribute -> dialectPrefix.equals(attribute.getAttributeDefinition().getAttributeName().getPrefix()))
        .forEach(attribute -> {
          Object resolvedValue = tryResolveAttributeValue(attribute, context, expressionParser);

          attributes.put(attribute.getAttributeCompleteName().substring(dialectPrefix.length() + 1), resolvedValue);
        });
    }

    return attributes;
  }

  private Map<String, Object> resolveAdditionalAttributes(IProcessableElementTag element, ITemplateContext context,
    IStandardExpressionParser expressionParser) {
    Map<String, Object> attributes = new HashMap<>();

    if (element.getAllAttributes() != null) {
      stream(element.getAllAttributes())
        .filter(attribute -> !dialectPrefix.equals(attribute.getAttributeDefinition().getAttributeName().getPrefix()))
        .forEach(attribute -> attributes.put(attribute.getAttributeCompleteName(),
          tryResolveAttributeValue(attribute, context, expressionParser)));
    }

    return attributes;
  }

  private static Object tryResolveAttributeValue(IAttribute attribute, ITemplateContext context,
    IStandardExpressionParser expressionParser) {
    try {
      return expressionParser.parseExpression(context, attribute.getValue()).execute(context);
    } catch (TemplateProcessingException e) {
      return attribute.getValue();
    }
  }

  private static IModel parseFragmentTemplateModel(ITemplateContext context, String templateName) {
    TemplateManager templateManager = context.getConfiguration().getTemplateManager();
    TemplateModel templateModel = templateManager.parseStandalone(context, templateName, emptySet(), HTML, true, true);

    return templateModel;
  }

  public static List<ITemplateEvent> subTreeBelow(IModel model, IProcessableElementTag elementTag) {
    List<ITemplateEvent> subTree = ComponentModelProcessor.subTreeFrom(model, elementTag);

    return subTree.size() < 2 ? emptyList() : subTree.subList(1, subTree.size() - 1);
  }

  static List<ITemplateEvent> subTreeFrom(IModel model, ITemplateEvent startTemplateEvent) {
    List<ITemplateEvent> subTree = new ArrayList<>();

    boolean startTemplateEventFound = false;
    int nrOfUnclosedOpenElementTags = 0;

    for (int i = 0; i < model.size(); i++) {
      ITemplateEvent templateEvent = model.get(i);

      if (templateEvent == startTemplateEvent) {
        startTemplateEventFound = true;
        subTree.add(templateEvent);
      }

      if (startTemplateEventFound) {
        if (nrOfUnclosedOpenElementTags > 0) {
          subTree.add(templateEvent);
        }

        if (templateEvent instanceof IOpenElementTag) {
          nrOfUnclosedOpenElementTags++;
        }

        if (templateEvent instanceof ICloseElementTag) {
          nrOfUnclosedOpenElementTags--;
        }
      }

      if (startTemplateEventFound && nrOfUnclosedOpenElementTags == 0) {
        break;
      }
    }

    return subTree;
  }

  private static List<ITemplateEvent> templateEventsIn(IModel model) {
    List<ITemplateEvent> templateEvents = new ArrayList<>();

    for (int i = 0; i < model.size(); i++) {
      templateEvents.add(model.get(i));
    }

    return templateEvents;
  }
}
