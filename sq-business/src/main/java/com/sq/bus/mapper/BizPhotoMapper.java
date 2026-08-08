package com.sq.bus.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sq.bus.domain.BizPhoto;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface BizPhotoMapper extends BaseMapper<BizPhoto> {

    /**
     * 按日期分组统计（yyyy-MM-dd）
     */
    List<Map<String, Object>> groupCountByDate(@Param("albumId") Long albumId);
}
