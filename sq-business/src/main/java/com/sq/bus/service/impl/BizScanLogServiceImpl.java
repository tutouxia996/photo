package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizScanLog;
import com.sq.bus.mapper.BizScanLogMapper;
import com.sq.bus.service.IBizScanLogService;
import org.springframework.stereotype.Service;

@Service
public class BizScanLogServiceImpl extends ServiceImpl<BizScanLogMapper, BizScanLog> implements IBizScanLogService {
}
