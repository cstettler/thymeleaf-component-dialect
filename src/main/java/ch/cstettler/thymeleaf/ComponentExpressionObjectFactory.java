package ch.cstettler.thymeleaf;

import java.util.Collections;
import java.util.Set;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

public class ComponentExpressionObjectFactory implements IExpressionObjectFactory {

  private final String objectName;

  ComponentExpressionObjectFactory(String objectName) {
    this.objectName = objectName;
  }

  @Override
  public Set<String> getAllExpressionObjectNames() {
    return objectName == null || objectName.isEmpty () ? Collections.emptySet () : Collections.singleton (objectName);
  }

  @Override
  public Object buildObject(IExpressionContext context, String expressionObjectName) {
    if (objectName.equals(expressionObjectName)) {
      return new ComponentExpressionObject (context);
    }
    return null;
  }

  @Override
  public boolean isCacheable(String expressionObjectName) {
    return false;
  }
}
