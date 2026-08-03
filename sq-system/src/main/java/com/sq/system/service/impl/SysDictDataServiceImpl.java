package com.sq.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.common.core.domain.entity.SysDictData;
import com.sq.common.utils.DictUtils;
import com.sq.common.utils.GeneratorUtil;
import com.sq.common.utils.StringUtils;
import com.sq.system.mapper.SysDictDataMapper;
import com.sq.system.service.ISysDictDataService;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 字典 业务层处理
 *
 * @author tzt
 */
@Service
public class SysDictDataServiceImpl implements ISysDictDataService {
    @Autowired
    private SysDictDataMapper dictDataMapper;

    private LambdaQueryWrapper<SysDictData> buildDictDataQuery(SysDictData dictData) {
        LambdaQueryWrapper<SysDictData> queryWrapper = new LambdaQueryWrapper<SysDictData>().orderByAsc(SysDictData::getDictSort);
        if (StringUtils.isNotEmpty(dictData.getDictType())) {
            queryWrapper.eq(SysDictData::getDictType, dictData.getDictType());
        }
        if (StringUtils.isNotEmpty(dictData.getDictLabel())) {
            queryWrapper.like(SysDictData::getDictLabel, dictData.getDictLabel());
        }
        if (StringUtils.isNotEmpty(dictData.getStatus())) {
            queryWrapper.eq(SysDictData::getStatus, dictData.getStatus());
        }
        return queryWrapper;
    }

    private List<SysDictData> selectActiveDictDataByType(String dictType) {
        return dictDataMapper.selectList(new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, "0")
                .orderByAsc(SysDictData::getDictSort));
    }

    /**
     * 根据条件分页查询字典数据
     *
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    @Override
    public List<SysDictData> selectDictDataList(SysDictData dictData) {
        return dictDataMapper.selectList(buildDictDataQuery(dictData));
    }

    /**
     * 根据字典类型和字典键值查询字典数据信息
     *
     * @param dictType  字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    @Override
    public String selectDictLabel(String dictType, String dictValue) {
        SysDictData dictData = dictDataMapper.selectOne(new LambdaQueryWrapper<SysDictData>()
                .select(SysDictData::getDictLabel)
                .eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getDictValue, dictValue)
                .last("limit 1"));
        return StringUtils.isNotNull(dictData) ? dictData.getDictLabel() : null;
    }

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    @Override
    public SysDictData selectDictDataById(Long dictCode) {
        return dictDataMapper.selectById(dictCode);
    }

    /**
     * 批量删除字典数据信息
     *
     * @param dictCodes 需要删除的字典数据ID
     */
    @Override
    public void deleteDictDataByIds(Long[] dictCodes) {
        for (Long dictCode : dictCodes) {
            SysDictData data = selectDictDataById(dictCode);
            dictDataMapper.deleteById(dictCode);
            List<SysDictData> dictDatas = selectActiveDictDataByType(data.getDictType());
            DictUtils.setDictCache(data.getDictType(), dictDatas);
        }
    }

    /**
     * 新增保存字典数据信息
     *
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    public int insertDictData(SysDictData data) {
        data.setId(GeneratorUtil.getNextId());
        int row = dictDataMapper.insertDictData(data);
        if (row > 0) {
            List<SysDictData> dictDatas = selectActiveDictDataByType(data.getDictType());
            DictUtils.setDictCache(data.getDictType(), dictDatas);
        }
        return row;
    }

    /**
     * 修改保存字典数据信息
     *
     * @param data 字典数据信息
     * @return 结果
     */
    @Override
    public int updateDictData(SysDictData data) {
        int row = dictDataMapper.updateDictData(data);
        if (row > 0) {
            List<SysDictData> dictDatas = selectActiveDictDataByType(data.getDictType());
            DictUtils.setDictCache(data.getDictType(), dictDatas);
        }
        return row;
    }

    @Override
    public SysDictData selectDictDataByDict(SysDictData dict) {
        LambdaQueryWrapper<SysDictData> queryWrapper = new LambdaQueryWrapper<SysDictData>();
        if (StringUtils.isNotEmpty(dict.getDictType())) {
            queryWrapper.eq(SysDictData::getDictType, dict.getDictType());
        }
        if (StringUtils.isNotEmpty(dict.getDictValue())) {
            queryWrapper.eq(SysDictData::getDictValue, dict.getDictValue());
        }
        if (StringUtils.isNotEmpty(dict.getDictLabel())) {
            queryWrapper.eq(SysDictData::getDictLabel, dict.getDictLabel());
        }
        return dictDataMapper.selectOne(queryWrapper.last("limit 1"));
    }

    @Override
    public Map<String, String> mapByType(String dictType) {
        SysDictData dictData = new SysDictData();
        dictData.setDictType(dictType);
        return dictDataMapper.selectList(buildDictDataQuery(dictData))
                .stream()
                .collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel));
    }

    @Override
    public <T, S> void relatedic(List<T> list, List<Function<T, S>> getFuns, List<BiConsumer<T, String>> setFuns, String... dicTypes) {
        if (CollectionUtils.isNotEmpty(list)) {
            for (int i = 0; i < getFuns.size(); i++) {
                String dicType = dicTypes[i];
                Function<T, S> getFun = getFuns.get(i);
                BiConsumer<T, String> setFun = setFuns.get(i);
                List<SysDictData> dictDatas = selectActiveDictDataByType(dicType);
                Map<String, String> dictMap = dictDatas.stream().collect(Collectors.toMap(SysDictData::getDictValue, SysDictData::getDictLabel));
                list.stream().forEach(item -> {
                    S source = getFun.apply(item);
                    if (source != null) {
                        String codeStr = source instanceof String ? (String) source : String.valueOf(source);
                        String nameStr = Arrays.stream(codeStr.split(",")).filter(StringUtils::isNotBlank).map(code -> dictMap.get(code)).collect(Collectors.joining(","));
                        setFun.accept(item, nameStr);
                    }
                });
            }
        }
    }

    @Override
    public List<SysDictData> selectDictDataByType(String dictType, String[] dictValues) {
        LambdaQueryWrapper<SysDictData> queryWrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getDictType, dictType)
                .orderByAsc(SysDictData::getDictSort);
        if (dictValues != null && dictValues.length > 0) {
            queryWrapper.in(SysDictData::getDictValue, Arrays.asList(dictValues));
        }
        return dictDataMapper.selectList(queryWrapper);
    }

}
