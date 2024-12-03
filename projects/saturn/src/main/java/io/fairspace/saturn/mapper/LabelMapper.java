package io.fairspace.saturn.mapper;

import org.apache.ibatis.annotations.Param;

public interface LabelMapper {

    String getId(@Param("type") String type,
                 @Param("label") String label);

}