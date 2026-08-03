package com.sq.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.sq.common.constant.UserConstants;
import com.sq.common.core.domain.AjaxResult;
import com.sq.common.core.domain.entity.SysDictData;
import com.sq.common.core.domain.entity.SysDictType;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.DictUtils;
import com.sq.common.utils.GeneratorUtil;
import com.sq.common.utils.StringUtils;
import com.sq.system.mapper.SysDictDataMapper;
import com.sq.system.mapper.SysDictTypeMapper;
import com.sq.system.service.ISysDictDataService;
import com.sq.system.service.ISysDictTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.awt.geom.Ellipse2D;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.sq.common.core.domain.AjaxResult.error;
import static com.sq.common.utils.SecurityUtils.getUsername;

/**
 * 字典 业务层处理
 *
 * @author tzt
 */
@Service
public class SysDictTypeServiceImpl implements ISysDictTypeService {
    @Autowired
    private SysDictTypeMapper dictTypeMapper;

    @Autowired
    private SysDictDataMapper dictDataMapper;
    @Autowired
    private ISysDictTypeService dictTypeService;
    @Autowired
    private ISysDictDataService dictDataService;

    private LambdaQueryWrapper<SysDictType> buildDictTypeQuery(SysDictType dictType) {
        LambdaQueryWrapper<SysDictType> queryWrapper = new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getId);
        if (StringUtils.isNotEmpty(dictType.getDictName())) {
            queryWrapper.like(SysDictType::getDictName, dictType.getDictName());
        }
        if (StringUtils.isNotEmpty(dictType.getDictType())) {
            queryWrapper.like(SysDictType::getDictType, dictType.getDictType());
        }
        if (StringUtils.isNotEmpty(dictType.getStatus())) {
            queryWrapper.eq(SysDictType::getStatus, dictType.getStatus());
        }
        return queryWrapper;
    }

    /**
     * 项目启动时，初始化字典到缓存
     */
    @PostConstruct
    public void init() {
        loadingDictCache();
    }

    /**
     * 根据条件分页查询字典类型
     *
     * @param dictType 字典类型信息
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictType> selectDictTypeList(SysDictType dictType) {
        return dictTypeMapper.selectList(buildDictTypeQuery(dictType));
    }

    /**
     * 根据所有字典类型
     *
     * @return 字典类型集合信息
     */
    @Override
    public List<SysDictType> selectDictTypeAll() {
        return dictTypeMapper.selectList(new LambdaQueryWrapper<SysDictType>().orderByAsc(SysDictType::getId));
    }

    /**
     * 根据字典类型查询字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictData> selectDictDataByType(String dictType) {
        List<SysDictData> dictDatas = DictUtils.getDictCache(dictType);
        if (StringUtils.isNotEmpty(dictDatas)) {
            return dictDatas;
        }
        dictDatas = dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, "0")
                .orderByAsc(SysDictData::getDictSort));
        if (StringUtils.isNotEmpty(dictDatas)) {
            DictUtils.setDictCache(dictType, dictDatas);
            return dictDatas;
        }
        return null;
    }

    /**
     * 根据字典类型ID查询信息
     *
     * @param dictId 字典类型ID
     * @return 字典类型
     */
    @Override
    public SysDictType selectDictTypeById(Long dictId) {
        return dictTypeMapper.selectById(dictId);
    }

    /**
     * 根据字典类型查询信息
     *
     * @param dictType 字典类型
     * @return 字典类型
     */
    @Override
    public SysDictType selectDictTypeByType(String dictType) {
        return dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dictType)
                .last("limit 1"));
    }

    /**
     * 批量删除字典类型信息
     *
     * @param dictIds 需要删除的字典ID
     */
    @Override
    public void deleteDictTypeByIds(Long[] dictIds) {
        for (Long dictId : dictIds) {
            SysDictType dictType = selectDictTypeById(dictId);
            if (dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictData>()
                    .eq(SysDictData::getDictType, dictType.getDictType())) > 0) {
                throw new ServiceException(String.format("%1$s已分配,不能删除", dictType.getDictName()));
            }
            dictTypeMapper.deleteById(dictId);
            DictUtils.removeDictCache(dictType.getDictType());
        }
    }

    /**
     * 加载字典缓存数据
     */
    @Override
    public void loadingDictCache() {
        SysDictData dictData = new SysDictData();
        dictData.setStatus("0");
        Map<String, List<SysDictData>> dictDataMap = dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getStatus, dictData.getStatus())
                .orderByAsc(SysDictData::getDictSort)).stream().collect(Collectors.groupingBy(SysDictData::getDictType));
        for (Map.Entry<String, List<SysDictData>> entry : dictDataMap.entrySet()) {
            DictUtils.setDictCache(entry.getKey(), entry.getValue().stream().sorted(Comparator.comparing(SysDictData::getDictSort)).collect(Collectors.toList()));
        }
    }

    /**
     * 清空字典缓存数据
     */
    @Override
    public void clearDictCache() {
        DictUtils.clearDictCache();
    }

    /**
     * 重置字典缓存数据
     */
    @Override
    public void resetDictCache() {
        clearDictCache();
        loadingDictCache();
    }

    /**
     * 新增保存字典类型信息
     *
     * @param dict 字典类型信息
     * @return 结果
     */
    @Override
    public int insertDictType(SysDictType dict) {
        dict.setId(GeneratorUtil.getNextId());
        int row = dictTypeMapper.insertDictType(dict);
        if (row > 0) {
            DictUtils.setDictCache(dict.getDictType(), null);
        }
        return row;
    }

    /**
     * 修改保存字典类型信息
     *
     * @param dict 字典类型信息
     * @return 结果
     */
    @Override
    @Transactional
    public int updateDictType(SysDictType dict) {
        SysDictType oldDict = dictTypeMapper.selectById(dict.getId());
        dictDataMapper.updateDictDataType(oldDict.getDictType(), dict.getDictType());
        int row = dictTypeMapper.updateDictType(dict);
        if (row > 0) {
            List<SysDictData> dictDatas = dictDataMapper.selectDictDataByType(dict.getDictType());
            DictUtils.setDictCache(dict.getDictType(), dictDatas);
        }
        return row;
    }

    /**
     * 校验字典类型称是否唯一
     *
     * @param dict 字典类型
     * @return 结果
     */
    @Override
    public String checkDictTypeUnique(SysDictType dict) {
        Long dictId = StringUtils.isNull(dict.getId()) ? -1L : dict.getId();
        SysDictType dictType = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, dict.getDictType())
                .last("limit 1"));
        if (StringUtils.isNotNull(dictType) && dictType.getId().longValue() != dictId.longValue()) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 新增字典
     *
     * @param dictType
     * @param dictDataList
     * @return AjaxResult
     * @author hnGeng
     * @date 2024/7/23 13:39
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public AjaxResult addDictTypeAndData(SysDictType dictType, List<SysDictData> dictDataList) {
        String username = getUsername();
        if (UserConstants.NOT_UNIQUE.equals(dictTypeService.checkDictTypeUnique(dictType))) {
            return error("字典'" + dictType.getDictName() + "'失败，字典类型已存在");
        }
        if (dictType.getId() != null) {
            dictType.setUpdateBy(username);
            dictTypeService.updateDictType(dictType);
        } else {
            dictType.setCreateBy(username);
            dictTypeService.insertDictType(dictType);
        }
        if (CollectionUtils.isNotEmpty(dictDataList)) {
            if (dictType.getId() != null) {
                //先删除在操作
                Long[] dictCodes = dictTypeService.selectAllDictDataByType(dictType.getDictType()).stream()
                        .map(SysDictData::getId).toArray(Long[]::new);
                dictDataService.deleteDictDataByIds(dictCodes);
            }
            dictDataList.forEach(dictData -> {
                if (org.apache.commons.lang3.StringUtils.isBlank(dictData.getDictValue())) {
                    dictData.setDictValue(IdWorker.getIdStr());
                }
                dictData.setDictType(dictType.getDictType());
                dictData.setListClass("default");
                if (dictData.getId() != null) {
                    dictData.setUpdateBy(username);
                } else {
                    dictData.setCreateBy(username);
                }
                dictDataService.insertDictData(dictData);
            });
        }

        return AjaxResult.success();
    }

    /**
     * 根据字典类型查询所有字典数据
     *
     * @param dictType 字典类型
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictData> selectAllDictDataByType(String dictType) {
        List<SysDictData> dictDatas = DictUtils.getDictCache(dictType);
        if (StringUtils.isNotEmpty(dictDatas)) {
            return dictDatas;
        }
        dictDatas = dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getDictSort));
        if (StringUtils.isNotEmpty(dictDatas)) {
            DictUtils.setDictCache(dictType, dictDatas);
            return dictDatas;
        }
        return null;
    }
}
