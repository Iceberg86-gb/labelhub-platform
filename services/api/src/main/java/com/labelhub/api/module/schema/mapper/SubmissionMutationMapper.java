package com.labelhub.api.module.schema.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SubmissionMutationMapper {

    @Update("UPDATE submissions SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Update("UPDATE submissions SET superseded_by_id = #{supersededById} WHERE id = #{id}")
    int updateSupersededBy(@Param("id") Long id, @Param("supersededById") Long supersededById);
}
