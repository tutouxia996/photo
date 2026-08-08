package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizPhoto;

import java.util.List;
import java.util.Map;

public interface IBizPhotoService extends IService<BizPhoto> {

    List<Map<String, Object>> groupCountByDate(Long albumId);

    BizPhoto findByMd5(String md5);
}
