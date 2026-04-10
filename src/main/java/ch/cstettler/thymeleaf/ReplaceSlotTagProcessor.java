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

import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.processor.element.AbstractElementTagProcessor;
import org.thymeleaf.processor.element.IElementTagStructureHandler;

import java.util.Map;

import static org.thymeleaf.standard.processor.StandardReplaceTagProcessor.PRECEDENCE;
import static org.thymeleaf.templatemode.TemplateMode.HTML;

class ReplaceSlotTagProcessor extends AbstractElementTagProcessor {

    static final String DEFAULT_SLOT_NAME = ReplaceSlotTagProcessor.class.getName() + ".default";
    static final String SLOTS_DATA = ReplaceSlotTagProcessor.class.getName() + ".slots-data";

    private final String dialectPrefix;

    public ReplaceSlotTagProcessor(String dialectPrefix, String elementName) {
        super(HTML, dialectPrefix, elementName, true, null, false, PRECEDENCE);
        this.dialectPrefix = dialectPrefix;
    }

    @Override
    protected void doProcess(ITemplateContext context, IProcessableElementTag tag, IElementTagStructureHandler structureHandler) {
        String slotName = tag.hasAttribute(dialectPrefix, "name") ? tag.getAttributeValue(dialectPrefix, "name") : DEFAULT_SLOT_NAME;

        Object slotsDataObj = context.getVariable(SLOTS_DATA);
        if (slotsDataObj instanceof SlotData slotsData && slotsData.contains(slotName)) {
            IModel model = slotsData.get(slotName);
            if (model != null) {
                structureHandler.setLocalVariable(SLOTS_DATA, slotsData.parent);
                structureHandler.setTemplateData(slotsData.templateData);
                structureHandler.replaceWith(model, true);
            } else {
                // remove the <pl:slot> tag, as no content was provided for this slot
                structureHandler.removeElement();
            }
        } else {
            // remove the <pl:slot> tag, but keep its original body
            structureHandler.removeTags();
        }
    }

    record SlotData(Map<String, IModel> slotsMap, TemplateData templateData, SlotData parent) {
        SlotData(Map<String, IModel> slotsMap, TemplateData templateData) {
            this(slotsMap, templateData, null);
        }

        boolean contains(String slotName) {
            return slotsMap.containsKey(slotName);
        }

        IModel get(String slotName) {
            return slotsMap.get(slotName);
        }
    }
}
