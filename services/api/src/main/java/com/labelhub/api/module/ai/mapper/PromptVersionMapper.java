package com.labelhub.api.module.ai.mapper;

import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PromptVersionMapper {

    @Insert("""
        INSERT INTO prompt_versions
        (version_no, content, content_hash, status, owner_id, published_at, created_at)
        VALUES
        (#{versionNumber}, #{content}, #{contentHash}, #{statusCode}, #{ownerId}, #{publishedAt}, NOW(3))
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(PromptVersionEntity entity);

    @Select("""
        SELECT id, version_no, content, content_hash, status, owner_id, published_at, created_at
        FROM prompt_versions
        WHERE id = #{id}
        """)
    @Results(id = "promptVersionResultMap", value = {
        @Result(column = "version_no", property = "versionNumber"),
        @Result(column = "content_hash", property = "contentHash"),
        @Result(column = "status", property = "statusCode"),
        @Result(column = "owner_id", property = "ownerId"),
        @Result(column = "published_at", property = "publishedAt"),
        @Result(column = "created_at", property = "createdAt")
    })
    PromptVersionEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, version_no, content, content_hash, status, owner_id, published_at, created_at
        FROM prompt_versions
        WHERE content_hash = #{contentHash}
        """)
    @Results(id = "promptVersionByHashResultMap", value = {
        @Result(column = "version_no", property = "versionNumber"),
        @Result(column = "content_hash", property = "contentHash"),
        @Result(column = "status", property = "statusCode"),
        @Result(column = "owner_id", property = "ownerId"),
        @Result(column = "published_at", property = "publishedAt"),
        @Result(column = "created_at", property = "createdAt")
    })
    PromptVersionEntity selectByContentHash(@Param("contentHash") String contentHash);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM prompt_versions")
    Integer selectMaxVersionNumber();

    @Select("""
        SELECT id, version_no, content, content_hash, status, owner_id, published_at, created_at
        FROM prompt_versions
        WHERE status = 'published'
        ORDER BY version_no DESC
        LIMIT 1
        """)
    @Results(id = "latestPublishedPromptVersionResultMap", value = {
        @Result(column = "version_no", property = "versionNumber"),
        @Result(column = "content_hash", property = "contentHash"),
        @Result(column = "status", property = "statusCode"),
        @Result(column = "owner_id", property = "ownerId"),
        @Result(column = "published_at", property = "publishedAt"),
        @Result(column = "created_at", property = "createdAt")
    })
    PromptVersionEntity selectLatestPublished();
}
