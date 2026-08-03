package com.sq.system.service;

import com.sq.common.core.domain.entity.SysDictData;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 字典 业务层
 *
 * @author tzt
 */
public interface ISysDictDataService {
    /**
     * 根据条件分页查询字典数据
     *
     * @param dictData 字典数据信息
     * @return 字典数据集合信息
     */
    public List<SysDictData> selectDictDataList(SysDictData dictData);

    /**
     * 根据字典类型和字典键值查询字典数据信息
     *
     * @param dictType  字典类型
     * @param dictValue 字典键值
     * @return 字典标签
     */
    public String selectDictLabel(String dictType, String dictValue);

    /**
     * 根据字典数据ID查询信息
     *
     * @param dictCode 字典数据ID
     * @return 字典数据
     */
    public SysDictData selectDictDataById(Long dictCode);

    /**
     * 批量删除字典数据信息
     *
     * @param dictCodes 需要删除的字典数据ID
     */
    public void deleteDictDataByIds(Long[] dictCodes);

    /**
     * 新增保存字典数据信息
     *
     * @param dictData 字典数据信息
     * @return 结果
     */
    public int insertDictData(SysDictData dictData);

    /**
     * 修改保存字典数据信息
     *
     * @param dictData 字典数据信息
     * @return 结果
     */
    public int updateDictData(SysDictData dictData);

    SysDictData selectDictDataByDict(SysDictData dict);

    Map<String, String> mapByType(String xingzheng);

    <T,S> void relatedic(List<T> list, List<Function<T, S>> getFuns, List<BiConsumer<T, String>> setFuns, String... dicTypes);

    /**
     * 根据条件搜索字典下拉数据
     * @param dictType 字典类型
     * @param dictValues 需要的字典值
     * @return 字典数据
     */
    List<SysDictData> selectDictDataByType(String dictType, String[] dictValues);
}
