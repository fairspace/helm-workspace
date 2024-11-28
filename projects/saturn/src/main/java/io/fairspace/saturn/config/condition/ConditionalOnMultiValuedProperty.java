package io.fairspace.saturn.config.condition;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Documented
@Conditional(OnMultiValuedPropertyCondition.class)
public @interface ConditionalOnMultiValuedProperty {
    String prefix();

    String name();

    String havingValue();
}
