package com.labelhub.api.module.session.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.session.entity.DraftEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DraftMapper {

    @Insert("""
        INSERT INTO drafts(session_id, revision_no, draft_payload, saved_at)
        VALUES (#{sessionId}, #{revisionNo},
                #{draftPayload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
                #{savedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DraftEntity entity);

    @Select("""
        SELECT id, session_id, revision_no, draft_payload, saved_at
        FROM drafts
        WHERE session_id = #{sessionId}
        ORDER BY revision_no DESC
        LIMIT 1
        """)
    @Results(id = "draftResultMap", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "session_id", property = "sessionId"),
        @Result(column = "revision_no", property = "revisionNo"),
        @Result(column = "draft_payload", property = "draftPayload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "saved_at", property = "savedAt")
    })
    DraftEntity selectLatestBySession(@Param("sessionId") Long sessionId);

    @Select("""
        SELECT id, session_id, revision_no, draft_payload, saved_at
        FROM drafts
        WHERE session_id = #{sessionId} AND revision_no = #{revisionNo}
        """)
    @ResultMap("draftResultMap")
    DraftEntity selectBySessionAndRevision(@Param("sessionId") Long sessionId, @Param("revisionNo") Integer revisionNo);

    @Select("""
        SELECT MAX(revision_no) FROM drafts WHERE session_id = #{sessionId}
        """)
    Integer selectMaxRevisionNumber(@Param("sessionId") Long sessionId);
}
