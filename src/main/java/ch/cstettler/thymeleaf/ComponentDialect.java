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

import java.util.HashSet;
import java.util.Set;
import org.thymeleaf.dialect.AbstractProcessorDialect;
import org.thymeleaf.processor.IProcessor;

public class ComponentDialect extends AbstractProcessorDialect {

  private static final String DEFAULT_DIALECT_PREFIX = "pl";

  private final Set<IProcessor> processors;

  public ComponentDialect() {
    this(DEFAULT_DIALECT_PREFIX);
  }

  public ComponentDialect(String prefix) {
    super("Thymeleaf UI Component Dialect", prefix, 0);

    this.processors = new HashSet<>();
    this.processors.add(new RemoveSlotAttributeProcessor(prefix, "slot"));
  }

  public ComponentDialect addComponent(String elementName, String templatePath) {
    processors.add(new ComponentModelProcessor(getPrefix (), elementName, templatePath));
    return this;
  }

  @Override
  public Set<IProcessor> getProcessors(String dialectPrefix) {
    return processors;
  }
}
