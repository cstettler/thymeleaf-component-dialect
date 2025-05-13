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
