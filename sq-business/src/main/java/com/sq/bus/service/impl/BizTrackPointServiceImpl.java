package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizTrackPoint;
import com.sq.bus.mapper.BizTrackPointMapper;
import com.sq.bus.service.IBizTrackPointService;
import org.springframework.stereotype.Service;

@Service
public class BizTrackPointServiceImpl extends ServiceImpl<BizTrackPointMapper, BizTrackPoint> implements IBizTrackPointService {
}
