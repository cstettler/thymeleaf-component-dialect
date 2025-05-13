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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.thymeleaf.IEngineConfiguration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.Context;
import org.thymeleaf.model.ICloseElementTag;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IOpenElementTag;
import org.thymeleaf.model.IStandaloneElementTag;
import org.thymeleaf.model.ITemplateEnd;
import org.thymeleaf.model.ITemplateEvent;
import org.thymeleaf.model.ITemplateStart;
import org.thymeleaf.model.IText;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;
import org.thymeleaf.templateresolver.TemplateResolution;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

class ComponentModelProcessorTest {

  @Test
  void simple_openAndCloseTag_renders() {
    String html = render("<pl:simple></pl:simple>");

    assertMarkupEquals("<i>simple</i>", html);
  }

  @Test
  void simple_standaloneTag_renders() {
    String html = render("<pl:simple />");

    assertMarkupEquals("<i>simple</i>", html);
  }

  @Disabled("additional attributes not yet fully supported")
  @Test
  void simple_additionalStaticAttribute_rendersAttribute() {
    String html = render("<pl:simple key='value' />");

    assertMarkupEquals("<i key='value'>simple</i>", html);
  }

  @Disabled("additional attributes not yet fully supported")
  @Test
  void simple_additionalDynamicAttribute_rendersAttribute() {
    String html = render("<pl:simple th:attr='key=value' />");

    assertMarkupEquals("<i key='value'>simple</i>", html);
  }

  @Test
  void simple_ifConditionTrue_renders() {
    String html = render("<pl:simple th:if='true' />");

    assertMarkupEquals("<i>simple</i>", html);
  }

  @Test
  void simple_ifConditionFalse_rendersNothing() {
    String html = render("<pl:simple th:if='false' />");

    assertMarkupEquals("", html);
  }

  @Test
  void simple_ifConditionViaParameter_renders() {
    String html = render(""
        + "<th:block th:with='value=true'>"
        + "  <pl:simple th:if='${value}' />"
        + "</th:block>"
    );

    assertMarkupEquals("<i>simple</i>", html);
  }

  @Test
  void withParameter_parameterDefined_rendersParameter() {
    String html = render("<pl:with-parameter pl:parameter='with-parameter-defined' />");

    assertMarkupEquals("<i>with-parameter-defined</i>", html);
  }

  @Test
  void withParameter_parameterNotDefined_renders() {
    String html = render("<pl:with-parameter />");

    assertMarkupEquals("<i></i>", html);
  }

  @Test
  void withDefaultSlot_slotContentDefined_rendersSlotContent() {
    String html = render(""
        + "<pl:with-default-slot>"
        + "  <i>slot-content</i>"
        + "</pl:with-default-slot>"
    );

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-default-slot</i>"
        + "  <i>slot-content</i>"
        + "</div>", html);
  }

  @Test
  void withDefaultSlot_slotContentNotDefined_rendersWithoutSlot() {
    String html = render("<pl:with-default-slot></pl:with-default-slot>");

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-default-slot</i>"
        + "</div>", html);
  }

  @Test
  void withNamedSlots_slotContentsDefined_rendersSlotContents() {
    String html = render(""
        + "<pl:with-named-slots>"
        + "  <i pl:slot='slot-a'>slot-content-a</i>"
        + "  <i pl:slot='slot-b'>slot-content-b</i>"
        + "</pl:with-named-slots>"
    );

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-named-slots</i>"
        + "  <div>"
        + "    <i>slot-content-a</i>"
        + "  </div>"
        + "  <div>"
        + "    <i>slot-content-b</i>"
        + "  </div>"
        + "</div>", html);
  }

  @Test
  void withNamedSlots_slotContentsOnlyPartiallyDefined_rendersDefinedSlotContents() {
    String html = render(""
        + "<pl:with-named-slots>"
        + "  <i pl:slot='slot-a'>slot-content-a</i>"
        + "</pl:with-named-slots>"
    );

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-named-slots</i>"
        + "  <div>"
        + "    <i>slot-content-a</i>"
        + "  </div>"
        + "  <div>"
        + "  </div>"
        + "</div>", html);
  }

  @Test
  void withDefaultAndNamedSlots_slotContentsDefined_rendersSlotContents() {
    String html = render(""
        + "<pl:with-default-and-named-slots>"
        + "  <i>default-slot-content</i>"
        + "  <i pl:slot='slot-a'>slot-content-a</i>"
        + "</pl:with-default-and-named-slots>"
    );

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-default-and-named-slots</i>"
        + "  <div>"
        + "    <i>default-slot-content</i>"
        + "  </div>"
        + "  <div>"
        + "    <i>slot-content-a</i>"
        + "  </div>"
        + "</div>", html);
  }

  @Test
  void withDefaultAndNamedSlots_multipleDefaultSlotContentsDefined_rendersSlotContents() {
    String html = render(""
        + "<pl:with-default-and-named-slots>"
        + "  <i>default-slot-content</i>"
        + "  <i>more-default-slot-content</i>"
        + "  <i pl:slot='slot-a'>slot-content-a</i>"
        + "</pl:with-default-and-named-slots>"
    );

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-default-and-named-slots</i>"
        + "  <div>"
        + "    <i>default-slot-content</i>"
        + "    <i>more-default-slot-content</i>"
        + "  </div>"
        + "  <div>"
        + "    <i>slot-content-a</i>"
        + "  </div>"
        + "</div>", html);
  }

  @Test
  void withSlotWithFallback_slotContentDefined_rendersSlotContent() {
    String html = render("<pl:with-slot-with-fallback><i>slot-content</i></pl:with-slot-with-fallback>");

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-slot-with-fallback</i>"
        + "  <i>slot-content</i>"
        + "</div>", html);
  }

  @Test
  void withSlotWithFallback_slotContentNotDefined_rendersFallback() {
    String html = render("<pl:with-slot-with-fallback></pl:with-slot-with-fallback>");

    assertMarkupEquals(""
        + "<div>"
        + "  <i>with-slot-with-fallback</i>"
        + "  <p>fallback</p>"
        + "</div>", html);
  }

  @Test
  void subTree_rootStartTemplateEvent_returnsCompleteTree() {
    ITemplateEvent startTemplateEvent = openElementTag();
    List<ITemplateEvent> templateEvents = List.of(
        startTemplateEvent,
        textElementTag(),
        closeElementTag()
    );

    IModel model = modelFor(templateEvents);

    List<ITemplateEvent> subTree = ComponentModelProcessor.subTreeFrom(model, startTemplateEvent);

    assertEquals(templateEvents, subTree);
  }

  @Test
  void subTree_nonRootStartTemplateEvent_returnsSubTree() {
    ITemplateEvent startTemplateEvent = openElementTag();
    List<ITemplateEvent> templateEvents = List.of(
        openElementTag(),
        startTemplateEvent,
        textElementTag(),
        closeElementTag(),
        closeElementTag()
    );

    IModel model = modelFor(templateEvents);

    List<ITemplateEvent> subTree = ComponentModelProcessor.subTreeFrom(model, startTemplateEvent);

    assertEquals(templateEvents.subList(1, 4), subTree);
  }

  @Test
  void subTree_fragmentRootAsStartTemplateEvent_returnsSubTree() {
    ITemplateEvent startTemplateEvent = openElementTag();
    List<ITemplateEvent> templateEvents = List.of(
        templateStart(),
        startTemplateEvent,
        textElementTag(),
        openElementTag(),
        textElementTag(),
        closeElementTag(),
        textElementTag(),
        closeElementTag(),
        textElementTag(),
        templateEnd()
    );

    IModel model = modelFor(templateEvents);

    List<ITemplateEvent> subTree = ComponentModelProcessor.subTreeFrom(model, startTemplateEvent);

    assertEquals(templateEvents.subList(1, 8), subTree);
  }

  @Test
  void subTree_standaloneTagAsStartTemplateEvent_returnsStandaloneTagOnly() {
    ITemplateEvent startTemplateEvent = standaloneElementTag();
    List<ITemplateEvent> templateEvents = List.of(
        openElementTag(),
        startTemplateEvent,
        textElementTag(),
        closeElementTag()
    );

    IModel model = modelFor(templateEvents);

    List<ITemplateEvent> subTree = ComponentModelProcessor.subTreeFrom(model, startTemplateEvent);

    assertEquals(List.of(startTemplateEvent), subTree);
  }

  @Test
  void subTree_multipleSiblingSubTrees_returnsMatchingSubTree() {
    ITemplateEvent startTemplateEvent = openElementTag();
    List<ITemplateEvent> templateEvents = List.of(
        startTemplateEvent,
        textElementTag(),
        closeElementTag(),
        openElementTag(),
        textElementTag(),
        closeElementTag()
    );

    IModel model = modelFor(templateEvents);

    List<ITemplateEvent> subTree = ComponentModelProcessor.subTreeFrom(model, startTemplateEvent);

    assertEquals(templateEvents.subList(0, 3), subTree);
  }

  @Test
  void subTree_emptyTemplateEvents_returnsEmptyList() {
    ITemplateEvent startTemplateEvent = openElementTag();
    IModel model = modelFor(emptyList());

    List<ITemplateEvent> subTree = ComponentModelProcessor.subTreeFrom(model, startTemplateEvent);

    assertEquals(emptyList(), subTree);
  }

  private static IModel modelFor(List<ITemplateEvent> templateEvents) {
    IModel model = mock(IModel.class);
    when(model.size()).thenReturn(templateEvents.size());
    when(model.get(anyInt())).thenAnswer(answer -> templateEvents.get(answer.getArgument(0, Integer.class)));
    return model;
  }

  private static ITemplateEvent templateStart() {
    return mock(ITemplateStart.class);
  }

  private static ITemplateEvent templateEnd() {
    return mock(ITemplateEnd.class);
  }

  private static ITemplateEvent openElementTag() {
    return mock(IOpenElementTag.class);
  }

  private static ITemplateEvent closeElementTag() {
    return mock(ICloseElementTag.class);
  }

  private static ITemplateEvent standaloneElementTag() {
    return mock(IStandaloneElementTag.class);
  }

  private static ITemplateEvent textElementTag() {
    return mock(IText.class);
  }

  private static void assertMarkupEquals(String expected, String actual) {
    assertEquals(trim(expected), trim(actual));
  }

  private static String trim(String value) {
    return value.replaceAll("^\\s+<", "<")
        .replaceAll(">\\s+<", "><")
        .replaceAll(">\\s+$", ">");
  }

  private static String render(String template) {
    ComponentDialect componentDialect = new ComponentDialect()
        .addComponent("simple", "components/simple.html")
        .addComponent("with-parameter", "components/with-parameter.html")
        .addComponent("with-default-and-named-slots", "components/with-default-and-named-slots.html")
        .addComponent("with-default-slot", "components/with-default-slot.html")
        .addComponent("with-named-slots", "components/with-named-slots.html")
        .addComponent("with-slot-with-fallback", "components/with-slot-with-fallback.html");

    TemplateEngine templateEngine = new TemplateEngine();
    templateEngine.setTemplateResolvers(setOf(new TemplateResolverChain(new ClassLoaderTemplateResolver(), new StringTemplateResolver())));
    templateEngine.addDialect(componentDialect);
    templateEngine.setCacheManager(null);
    templateEngine.clearTemplateCache();

    String result = templateEngine.process(new TemplateSpec(template, HTML), new Context());

    return result.trim();
  }

  @SafeVarargs
  private static <T> Set<T> setOf(T... items) {
    return new LinkedHashSet<>(asList(items));
  }

  private static class TemplateResolverChain implements ITemplateResolver {

    private final ITemplateResolver[] templateResolvers;

    private TemplateResolverChain(ITemplateResolver... templateResolvers) {
      this.templateResolvers = templateResolvers;
    }

    @Override
    public String getName() {
      return getClass().getName();
    }

    @Override
    public Integer getOrder() {
      return null;
    }

    @Override
    public TemplateResolution resolveTemplate(
        IEngineConfiguration configuration, String ownerTemplate, String template, Map<String, Object> templateResolutionAttributes
    ) {
      return Arrays.stream(templateResolvers)
          .map(templateResolver -> templateResolver.resolveTemplate(configuration, ownerTemplate, template, templateResolutionAttributes))
          .filter(templateResolution -> templateResolution != null && templateResolution.getTemplateResource().exists())
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("no template resolver found for template '" + template + "'"));
    }
  }
}