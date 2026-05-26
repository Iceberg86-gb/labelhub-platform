package com.labelhub.api.module.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labelhub.api.module.dataset.entity.DatasetEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetMapper extends BaseMapper<DatasetEntity> {
}
