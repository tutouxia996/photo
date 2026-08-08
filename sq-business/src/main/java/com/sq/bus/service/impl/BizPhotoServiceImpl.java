package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizPhoto;
import com.sq.bus.mapper.BizPhotoMapper;
import com.sq.bus.service.IBizPhotoService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class BizPhotoServiceImpl extends ServiceImpl<BizPhotoMapper, BizPhoto> implements IBizPhotoService {

    @Override
    public List<Map<String, Object>> groupCountByDate(Long albumId) {
        if (albumId == null) {
            return Collections.emptyList();
        }
        return baseMapper.groupCountByDate(albumId);
    }

    @Override
    public BizPhoto findByMd5(String md5) {
        if (md5 == null || md5.isEmpty()) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<BizPhoto>().eq(BizPhoto::getMd5, md5).last("limit 1"), false);
    }
}
