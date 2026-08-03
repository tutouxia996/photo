package com.sq.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.common.annotation.DataSource;
import com.sq.common.constant.CacheConstants;
import com.sq.common.constant.UserConstants;
import com.sq.common.core.redis.RedisCache;
import com.sq.common.core.text.Convert;
import com.sq.common.enums.DataSourceType;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.GeneratorUtil;
import com.sq.common.utils.StringUtils;
import com.sq.system.domain.SysConfig;
import com.sq.system.mapper.SysConfigMapper;
import com.sq.system.service.ISysConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;

/**
 * 参数配置 服务层实现
 * 
 * @author tzt
 */
@Service
public class SysConfigServiceImpl implements ISysConfigService {
    @Autowired
    private SysConfigMapper configMapper;

    @Autowired
    private RedisCache redisCache;

    private LambdaQueryWrapper<SysConfig> buildConfigQuery(SysConfig config) {
        LambdaQueryWrapper<SysConfig> queryWrapper = new LambdaQueryWrapper<SysConfig>().orderByAsc(SysConfig::getId);
        if (StringUtils.isNotNull(config.getId())) {
            queryWrapper.eq(SysConfig::getId, config.getId());
        }
        if (StringUtils.isNotEmpty(config.getConfigName())) {
            queryWrapper.like(SysConfig::getConfigName, config.getConfigName());
        }
        if (StringUtils.isNotEmpty(config.getConfigType())) {
            queryWrapper.eq(SysConfig::getConfigType, config.getConfigType());
        }
        if (StringUtils.isNotEmpty(config.getConfigKey())) {
            queryWrapper.like(SysConfig::getConfigKey, config.getConfigKey());
        }
        return queryWrapper;
    }

    /**
     * 项目启动时，初始化参数到缓存
     */
    @PostConstruct
    public void init() {
        loadingConfigCache();
    }

    /**
     * 查询参数配置信息
     * 
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    @DataSource(DataSourceType.MASTER)
    public SysConfig selectConfigById(Long configId) {
        return configMapper.selectById(configId);
    }

    /**
     * 根据键名查询参数配置信息
     * 
     * @param configKey 参数key
     * @return 参数键值
     */
    @Override
    public String selectConfigByKey(String configKey) {
        String configValue = Convert.toStr(redisCache.getCacheObject(getCacheKey(configKey)));
        if (StringUtils.isNotEmpty(configValue)) {
            return configValue;
        }
        SysConfig retConfig = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, configKey)
                .last("limit 1"));
        if (StringUtils.isNotNull(retConfig)) {
            redisCache.setCacheObject(getCacheKey(configKey), retConfig.getConfigValue());
            return retConfig.getConfigValue();
        }
        return StringUtils.EMPTY;
    }

    /**
     * 获取验证码开关
     * 
     * @return true开启，false关闭
     */
    @Override
    public boolean selectCaptchaEnabled() {
        return true;
    }

    /**
     * 查询参数配置列表
     * 
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<SysConfig> selectConfigList(SysConfig config) {
        return configMapper.selectList(buildConfigQuery(config));
    }

    /**
     * 新增参数配置
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public int insertConfig(SysConfig config) {
        config.setId(GeneratorUtil.getNextId());
        int row = configMapper.insertConfig(config);
        if (row > 0) {
            redisCache.setCacheObject(getCacheKey(config.getConfigKey()), config.getConfigValue());
        }
        return row;
    }

    /**
     * 修改参数配置
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public int updateConfig(SysConfig config) {
        SysConfig temp = configMapper.selectById(config.getId());
        if (!StringUtils.equals(temp.getConfigKey(), config.getConfigKey())) {
            redisCache.deleteObject(getCacheKey(temp.getConfigKey()));
        }

        int row = configMapper.updateConfig(config);
        if (row > 0) {
            redisCache.setCacheObject(getCacheKey(config.getConfigKey()), config.getConfigValue());
        }
        return row;
    }

    /**
     * 批量删除参数信息
     * 
     * @param configIds 需要删除的参数ID
     */
    @Override
    public void deleteConfigByIds(Long[] configIds) {
        for (Long configId : configIds) {
            SysConfig config = selectConfigById(configId);
            if (StringUtils.equals(UserConstants.YES, config.getConfigType())) {
                throw new ServiceException(String.format("内置参数【%1$s】不能删除 ", config.getConfigKey()));
            }
            configMapper.deleteById(configId);
            redisCache.deleteObject(getCacheKey(config.getConfigKey()));
        }
    }

    /**
     * 加载参数缓存数据
     */
    @Override
    public void loadingConfigCache() {
        List<SysConfig> configsList = configMapper.selectList(new LambdaQueryWrapper<SysConfig>());
        for (SysConfig config : configsList) {
            redisCache.setCacheObject(getCacheKey(config.getConfigKey()), config.getConfigValue());
        }
    }

    /**
     * 清空参数缓存数据
     */
    @Override
    public void clearConfigCache() {
        Collection<String> keys = redisCache.keys(CacheConstants.SYS_CONFIG_KEY + "*");
        redisCache.deleteObject(keys);
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    public void resetConfigCache() {
        clearConfigCache();
        loadingConfigCache();
    }

    /**
     * 校验参数键名是否唯一
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    @Override
    public String checkConfigKeyUnique(SysConfig config) {
        Long configId = StringUtils.isNull(config.getId()) ? -1L : config.getId();
        SysConfig info = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                .eq(SysConfig::getConfigKey, config.getConfigKey())
                .last("limit 1"));
        if (StringUtils.isNotNull(info) && info.getId().longValue() != configId.longValue()) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 设置cache key
     * 
     * @param configKey 参数键
     * @return 缓存键key
     */
    private String getCacheKey(String configKey) {
        return CacheConstants.SYS_CONFIG_KEY + configKey;
    }
}
