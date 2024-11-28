package io.fairspace.saturn.config.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ViewsProperties {

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public List<View> views = new ArrayList<>();

    private Map<String, View> nameToViewConfigMap;

    public Optional<View> getViewConfig(String viewName) {
        return Optional.ofNullable(nameToViewConfigMap.get(viewName));
    }

    public void init() {
        nameToViewConfigMap = views.stream().collect(Collectors.toMap(view -> view.name, Function.identity()));
    }

    public enum ColumnType {
        Text,
        Set,
        Term,
        TermSet,
        Number,
        Date,
        Boolean,
        Identifier;

        public boolean isSet() {
            return this == Set || this == TermSet;
        }

        @JsonValue
        public String getName() {
            return this.name();
        }

        private static final Map<String, ColumnType> mapping = new HashMap<>();

        static {
            for (ColumnType type : values()) {
                mapping.put(type.name().toLowerCase(), type);
            }
        }

        @JsonCreator
        public static ColumnType forName(String name) {
            if (name == null) {
                return null;
            }
            name = name.toLowerCase();
            if (!mapping.containsKey(name)) {
                throw new IllegalArgumentException("Unknown column type: " + name);
            }
            return mapping.get(name);
        }
    }

    public static class View {
        /**
         * The view name.
         */
        @NotBlank
        public String name;
        /**
         * The view title.
         */
        @NotBlank
        public String title;
        /**
         * The name of the items that appear as rows.
         */
        public String itemName;
        /**
         * The max count value to be requested for a view total count.
         * If total count is greater than this max value, the total count value will look like 'more than 1000' on FE.
         * This is to prevent performance issues when the total count is too large.
         */
        public Long maxDisplayCount;
        /**
         * The URLs of the types of entities that should be indexed in this view.
         */
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public List<String> types;
        /**
         * Specifies which other views (and which columns) to embed in this view.
         */
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public List<JoinView> join;
        /**
         * The columns of the view, not including columns from joined views.
         */
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public List<Column> columns;

        public static class Column {
            @NotBlank
            public String name;

            @NotBlank
            public String title;

            @NotNull
            public ColumnType type;

            @NotBlank
            public String source;
            // displayIndex determines the order of columns on the view page.
            @NotNull
            public Integer displayIndex = Integer.MAX_VALUE;

            public String rdfType;

            public int priority;
        }

        public static class JoinView {
            @NotBlank
            public String view;

            @NotBlank
            public String on;

            public boolean reverse = false;

            @JsonSetter(nulls = Nulls.AS_EMPTY)
            public List<String> include;
            // displayIndex determines the order of columns on the view page, for joinView it is the column displaying
            // the related entity
            public Integer displayIndex = Integer.MAX_VALUE;
        }
    }
}
