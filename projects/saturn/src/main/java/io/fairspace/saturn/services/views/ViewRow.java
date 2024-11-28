package io.fairspace.saturn.services.views;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Sets;

import io.fairspace.saturn.controller.dto.ValueDto;

public class ViewRow {

    private final Map<String, Set<ValueDto>> data;

    public ViewRow() {
        this.data = new HashMap<>();
    }

    public ViewRow(Map<String, Set<ValueDto>> data) {
        this.data = data;
    }

    public static ViewRow viewSetOf(ResultSet resultSet, List<String> columnsNames, String viewName)
            throws SQLException {
        var data = new HashMap<String, Set<ValueDto>>();
        for (String columnName : columnsNames) {
            String label = resultSet.getString(columnName);
            var key = viewName + "_" + columnName;
            var value = Sets.newHashSet(new ValueDto(label, label));
            data.put(key, value);
        }
        return new ViewRow(data);
    }

    // TODO, make obsolete by ViewStoreReader refactor
    // TODO: return unmodifiable map
    public Map<String, Set<ValueDto>> getRawData() {
        return data;
    }

    public void put(String key, Set<ValueDto> value) {
        data.put(key, value);
    }

    public ViewRow merge(ViewRow anotherViewRow) {
        anotherViewRow.getRawData().forEach((key, value) -> data.merge(key, value, ViewRow::addElementsAndReturn));
        return this;
    }

    private static Set<ValueDto> addElementsAndReturn(Set<ValueDto> set, Set<ValueDto> elements) {
        set.addAll(elements);
        return set;
    }
}
