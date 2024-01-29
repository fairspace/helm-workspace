package io.fairspace.saturn.services.views;

import com.google.common.collect.Sets;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewRow {

    private final Map<String, Set<ValueDTO>> data;

    public ViewRow() {
        this.data = new HashMap<>();
    }

    public ViewRow(Map<String, Set<ValueDTO>> data) {
        this.data = data;
    }

    public static ViewRow viewSetOf(ResultSet resultSet, List<String> columnsNames, String viewName) throws SQLException {
        var data = new HashMap<String, Set<ValueDTO>>();
        for (String columnName : columnsNames) {
            String label = resultSet.getString(columnName);
            var key = viewName + "_" + columnName;
            var value = Sets.newHashSet(new ValueDTO(label, label));
            data.put(key, value);
        }
        return new ViewRow(data);
    }

    // TODO, make obsolete by ViewStoreReader refactor
    // TODO: return unmodifiable map
    public Map<String, Set<ValueDTO>> getRawData() {
        return data;
    }

    public void put(String key, Set<ValueDTO> value) {
        data.put(key, value);
    }

    public ViewRow merge(ViewRow anotherViewRow) {
        anotherViewRow.getRawData().forEach((key, value) -> data.merge(key, value, ViewRow::addElementsAndReturn));
        return this;
    }

    private static Set<ValueDTO> addElementAndReturn(Set<ValueDTO> set, ValueDTO element) {
        set.add(element);
        return set;
    }

    private static Set<ValueDTO> addElementsAndReturn(Set<ValueDTO> set, Set<ValueDTO> elements) {
        set.addAll(elements);
        return set;
    }
}



