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
import static java.util.Collections.*;
import static org.thymeleaf.model.AttributeValueQuotes.DOUBLE;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.thymeleaf.IEngineConfiguration;
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
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.processor.element.AbstractElementModelProcessor;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import org.thymeleaf.standard.expression.*;
import org.thymeleaf.util.EscapedAttributeUtils;
import org.thymeleaf.util.StringUtils;

class ComponentModelProcessor extends AbstractElementModelProcessor {

  /*
   * Execute between `th:with` (precedence=600) and `th:attr` (precedence=700).
   * This means, attributes like th:if or th:each will be executed before the component is processed, but attributes like th:attr will be executed after.
   * This allows to use for example `th:if`, `th:each` on the component tag, but allows to use th:attr to be passed down using `pass-additional-attributes`.
   * And `th:object` and `th:with` can be used on the component tag to define variables that are available in the slots, and not affected by pass-additional-attributes.
   */
  public static final int PRECEDENCE = 650;
  private static final String TH_FRAGMENT = "th:fragment";

  private final String dialectPrefix;
  private final String elementName;
  private final String templatePath;
  private final String templateFragment;

  public ComponentModelProcessor(String dialectPrefix, String elementName, String templatePath) {
    super(HTML, dialectPrefix, elementName, true, null, false, PRECEDENCE);

    this.dialectPrefix = dialectPrefix;
    this.elementName = elementName;
    if (templatePath == null) {
      this.templatePath = dialectPrefix + "/" + elementName + "/" + elementName;
      this.templateFragment = elementName;
    } else if (templatePath.contains("::")) {
      int pos = templatePath.indexOf("::");
      this.templatePath = templatePath.substring(0, pos).trim();;
      this.templateFragment = templatePath.substring(pos + 2).trim();;
    } else {
      this.templatePath = templatePath;
      this.templateFragment = elementName;
    }
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
    IEngineConfiguration configuration = context.getConfiguration();
    IStandardExpressionParser expressionParser = StandardExpressions.getExpressionParser(configuration);
    Map<String, Object> additionalAttributes = getAdditionalAttributes (componentElementTag);
    Map<String, Object> componentAttributes = resolveComponentAttributes(componentElementTag, context, expressionParser);
    componentAttributes.forEach(structureHandler::setLocalVariable);

    TemplateModel fragmentModel = loadFragmentModel(context);
    IProcessableElementTag fragmentRootElementTag = firstOpenElementTagWithAttribute(fragmentModel, TH_FRAGMENT);
    if (fragmentRootElementTag != null) {
      Map<String, Object> defaultAttributes = resolveComponentAttributes (fragmentRootElementTag, context, expressionParser);
      componentAttributes.keySet ().forEach (defaultAttributes::remove);
      defaultAttributes.forEach (structureHandler::setLocalVariable);

      // Validate fragment signature:
      // all attributes must be present in componentAttributes
      // (this is analogous to how th:replace/th:insert works; see AbstractStandardFragmentInsertionTagProcessor)
      final String fragmentSignatureSpec = EscapedAttributeUtils.unescapeAttribute(fragmentModel.getTemplateMode(), fragmentRootElementTag.getAttributeValue(TH_FRAGMENT));
      if (!StringUtils.isEmptyOrWhitespace(fragmentSignatureSpec)) {
        final FragmentSignature fragmentSignature =
                FragmentSignatureUtils.parseFragmentSignature(configuration, fragmentSignatureSpec);
        if (fragmentSignature != null && fragmentSignature.hasParameters()) {
          for (String parameterName : fragmentSignature.getParameterNames()) {
            if (!componentAttributes.containsKey(parameterName)) {
              throw new TemplateProcessingException(
                      "Component '" + dialectPrefix + ":" + elementName + "' is missing required attribute '" +
                              parameterName + "' declared in fragment signature '" + fragmentSignatureSpec + "'");
            }
          }
        }
      }
    }

    /*
     * APPLY THE FRAGMENT'S TEMPLATE RESOLUTION so that all code inside the fragment is executed with its own
     * template resolution info (working as if it were a local variable)
     */
    structureHandler.setTemplateData(fragmentModel.getTemplateData());

    Map<String, List<ITemplateEvent>> slotContents = extractSlotContents(model);
    Map<String, IModel> slotModels = new HashMap<>(slotContents.size());
    slotContents.forEach((slotName, slotContentEvents) -> {
      IModel slotModel = context.getModelFactory().createModel();
      slotContentEvents.forEach(slotModel::add);
      slotModels.put(slotName, slotModel);
    });
    final ReplaceSlotTagProcessor.SlotData slotData;
    if (context.getVariable(ReplaceSlotTagProcessor.SLOTS_DATA) instanceof ReplaceSlotTagProcessor.SlotData parent) {
      slotData = new ReplaceSlotTagProcessor.SlotData(slotModels, context.getTemplateData(), parent);
    } else {
      slotData = new ReplaceSlotTagProcessor.SlotData(slotModels, context.getTemplateData());
    }
    structureHandler.setLocalVariable(ReplaceSlotTagProcessor.SLOTS_DATA, slotData);

    IModel mergedModel = prepareModel(context, fragmentModel, fragmentRootElementTag, additionalAttributes);

    model.reset();
    model.addModel(mergedModel);
  }

  private boolean isValidComponentTag(IProcessableElementTag componentElementTag) {
    return componentElementTag.getElementCompleteName().startsWith(dialectPrefix + ":");
  }

  private TemplateModel loadFragmentModel(ITemplateContext context) {
    return parseFragmentTemplateModel(context, templatePath, templateFragment);
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
    if (!defaultSlotContent.isEmpty()) {
      slots.values().forEach(defaultSlotContent::removeAll);
      slots.put(ReplaceSlotTagProcessor.DEFAULT_SLOT_NAME, defaultSlotContent);
    }
    return slots;
  }

  private IModel prepareModel(
    ITemplateContext context,
    IModel fragmentModel,
    IProcessableElementTag fragmentRootElementTag,
    Map<String, Object> additionalAttributes
  ) {
    IModelFactory modelFactory = context.getModelFactory();
    IModel newModel = modelFactory.createModel();

    List<ITemplateEvent> fragmentElementTags = subTreeBelow(fragmentModel, fragmentRootElementTag);
    boolean hasPassedDownAttributes = replaceAdditionalAttributes(fragmentElementTags, modelFactory, additionalAttributes);
    if (!hasPassedDownAttributes) {
      newModel.add(blockOpenElement(modelFactory, additionalAttributes));
    }

    fragmentElementTags.forEach(newModel::add);

    if (!hasPassedDownAttributes) {
      newModel.add (blockCloseElement (modelFactory));
    }

    return newModel;
  }

  private static Map<String,String> convertObjectMapToStringMap(Map<String, Object> objectMap) {
    Map<String, String> stringMap = new HashMap<>(objectMap.size ());
    objectMap.forEach((key, value) -> stringMap.put(key, value != null ? value.toString() : null));
    return stringMap;
  }

  private boolean replaceAdditionalAttributes(List<ITemplateEvent> fragmentElementTags, IModelFactory modelFactory, Map<String, Object> additionalAttributes) {
    boolean replaced = false;
    Set<String> removeAttributes = Collections.singleton (dialectPrefix + ":pass-additional-attributes");
    for (int i = 0; i < fragmentElementTags.size(); i++) {
      ITemplateEvent templateEvent = fragmentElementTags.get(i);
      if (templateEvent instanceof IProcessableElementTag elementTag
          && elementTag.hasAttribute (dialectPrefix, "pass-additional-attributes")) {
        fragmentElementTags.set(i, copyTagWithModifiedAttributes (elementTag, modelFactory, additionalAttributes, removeAttributes));
        replaced = true;
      }
    }
    return replaced;
  }

  private IProcessableElementTag copyTagWithModifiedAttributes (IProcessableElementTag elementTag, IModelFactory modelFactory, Map<String, Object> additionalAttributes, Set<String> removeAttributes) {
    Map<String,String> newAttributes = additionalAttributes == null ? new HashMap<> () : convertObjectMapToStringMap(additionalAttributes);
    newAttributes.putAll (elementTag.getAttributeMap ());
    if (removeAttributes != null) {
      removeAttributes.forEach (newAttributes::remove);
    }

    if (elementTag instanceof IOpenElementTag) {
      return modelFactory.createOpenElementTag (
          elementTag.getElementCompleteName (),
          newAttributes,
          DOUBLE,
          false
      );
    } else if (elementTag instanceof IStandaloneElementTag standaloneElementTag) {
      return modelFactory.createStandaloneElementTag (
          elementTag.getElementCompleteName (),
          newAttributes,
          DOUBLE,
          false,
          standaloneElementTag.isMinimized ()
      );
    }
    throw new IllegalArgumentException ("Unsupported tag class");
  }

  private static IOpenElementTag blockOpenElement(IModelFactory modelFactory, Map<String, Object> attributes) {
    Map<String, String> attributesMap = convertObjectMapToStringMap (attributes);

    return modelFactory.createOpenElementTag("th:block", attributesMap, DOUBLE, false);
  }

  private static ICloseElementTag blockCloseElement(IModelFactory modelFactory) {
    return modelFactory.createCloseElementTag("th:block");
  }

  private boolean isOpenOrStandaloneTag(ITemplateEvent templateEvent) {
    return templateEvent instanceof IProcessableElementTag;
  }

  private static IProcessableElementTag firstOpenOrStandaloneElementTag(IModel model) {
    return templateEventsIn(model)
      .filter(elementTag -> elementTag instanceof IProcessableElementTag)
      .map(templateEvent -> (IProcessableElementTag)templateEvent)
      .findFirst()
      .orElse(null);
  }

  private static IProcessableElementTag firstOpenElementTagWithAttribute(IModel model, String attributeName) {
    return templateEventsIn(model)
      .filter(elementTag -> elementTag instanceof IOpenElementTag)
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
          String attributeName = kebabToCamelCase(attribute.getAttributeDefinition().getAttributeName().getAttributeName());
          attributes.put(attributeName, resolvedValue);
        });
    }

    return attributes;
  }

  private Map<String, Object> getAdditionalAttributes (IProcessableElementTag element) {
    Map<String, Object> attributes = new HashMap<>();

    if (element.getAllAttributes() != null) {
      stream(element.getAllAttributes())
        .filter(attribute -> !dialectPrefix.equals(attribute.getAttributeDefinition().getAttributeName().getPrefix()))
        .forEach(attribute -> attributes.put(attribute.getAttributeCompleteName(), attribute.getValue ()));
    }

    return attributes;
  }

  private static Object tryResolveAttributeValue(IAttribute attribute, ITemplateContext context,
    IStandardExpressionParser expressionParser) {
    String value = attribute.getValue();
    if (value == null) {
      return null;
    }
    try {
      return expressionParser.parseExpression(context, value).execute(context);
    } catch (TemplateProcessingException e) {
      return value;
    }
  }

  private static TemplateModel parseFragmentTemplateModel(ITemplateContext context, String templateName, String fragmentName) {
    TemplateManager templateManager = context.getConfiguration().getTemplateManager();
    TemplateModel model =  templateManager.parseStandalone(context, templateName, fragmentName == null ? emptySet() : singleton(fragmentName), HTML, true, true);
    if (model == null) {
      throw new TemplateProcessingException("Could not load component template '" + templateName + "'");
    } else if (fragmentName != null && model.size() == 2) {
      // 2 = template start and template end events only -> fragment not found
      throw new TemplateProcessingException("Could not load component template '" + templateName + "::" + fragmentName + "'");
    }
    return model;
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

  private static Stream<ITemplateEvent> templateEventsIn(IModel model) {
    return IntStream.range(0, model.size()).mapToObj (model::get);
  }

  private static String kebabToCamelCase(String kebabCase) {
    StringBuilder camelCase = new StringBuilder();
    boolean toUpperCase = false;
    for (char c : kebabCase.toCharArray()) {
      if (c == '-') {
        toUpperCase = true;
      } else {
        if (toUpperCase) {
          camelCase.append(Character.toUpperCase(c));
          toUpperCase = false;
        } else {
          camelCase.append(c);
        }
      }
    }
    return camelCase.toString();
  }
}
