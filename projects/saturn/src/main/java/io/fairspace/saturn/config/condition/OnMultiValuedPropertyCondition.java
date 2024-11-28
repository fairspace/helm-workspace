package io.fairspace.saturn.config.condition;

import java.util.ArrayList;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class OnMultiValuedPropertyCondition extends SpringBootCondition {
    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        MergedAnnotation<ConditionalOnMultiValuedProperty> conditionalMultiValuedPropertyAnnotation =
                metadata.getAnnotations().get(ConditionalOnMultiValuedProperty.class.getName());
        var prefix = conditionalMultiValuedPropertyAnnotation.getString("prefix");
        var name = conditionalMultiValuedPropertyAnnotation.getString("name");

        var values = new ArrayList<>();
        Binder.get(context.getEnvironment()).bind(prefix + "." + name, Bindable.ofInstance(values));

        String valueToCheck = conditionalMultiValuedPropertyAnnotation.getString("havingValue");
        boolean valueExists = values.stream().anyMatch(valueToCheck::equals);
        if (valueExists) {
            return ConditionOutcome.match();
        } else {
            return ConditionOutcome.noMatch("define your message!");
        }
    }
}
