package io.fairspace.saturn.mapper;

import io.fairspace.saturn.services.views.Range;
import io.fairspace.saturn.services.views.ViewQueryParameters;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ViewMapper {

    Integer getTotalCount(@Param("viewType") String viewType,
                          @Param("query") ViewQueryParameters queryParameters);

    List<ViewRelation> selectParentViewRelationsPaginated(@Param("viewType") String viewType,
                                                          @Param("query") ViewQueryParameters queryParameters,
                                                          @Param("offset") int offset,
                                                          @Param("limit") int limit);

    List<ViewAttributeString> selectStringViewAttributes(@Param("viewType") String viewType,
                                                         @Param("viewNames") List<String> viewNames);

    List<ViewAttributeInt> selectIntViewAttributes(@Param("viewType") String viewType,
                                                   @Param("viewNames") List<String> viewNames);

    List<ViewAttributeDate> selectDateViewAttributes(@Param("viewType") String viewType,
                                                     @Param("viewNames") List<String> viewNames);

    Range selectNumericViewAttributeMinMax(@Param("viewType") String viewType,
                                           @Param("attributeName") String attributeName);

    Range selectDateViewAttributeMinMax(@Param("viewType") String viewType,
                                        @Param("attributeName") String attributeName);

}