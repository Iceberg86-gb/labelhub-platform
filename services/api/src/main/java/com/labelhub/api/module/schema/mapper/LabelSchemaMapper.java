package com.labelhub.api.module.schema.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.api.module.schema.entity.LabelSchemaEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LabelSchemaMapper extends BaseMapper<LabelSchemaEntity> {

    @Select("""
        SELECT * FROM label_schemas WHERE id = #{id} FOR UPDATE
        """)
    LabelSchemaEntity selectByIdForUpdate(@Param("id") Long id);
}
