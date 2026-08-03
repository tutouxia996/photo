package com.sq.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sq.common.constant.UserConstants;
import com.sq.common.exception.ServiceException;
import com.sq.common.utils.GeneratorUtil;
import com.sq.common.utils.StringUtils;
import com.sq.system.domain.SysPost;
import com.sq.system.mapper.SysPostMapper;
import com.sq.system.mapper.SysUserPostMapper;
import com.sq.system.service.ISysPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 岗位信息 服务层处理
 * 
 * @author tzt
 */
@Service
public class SysPostServiceImpl implements ISysPostService {
    @Autowired
    private SysPostMapper postMapper;

    @Autowired
    private SysUserPostMapper userPostMapper;

    private LambdaQueryWrapper<SysPost> buildPostQuery(SysPost post) {
        LambdaQueryWrapper<SysPost> queryWrapper = new LambdaQueryWrapper<SysPost>().orderByAsc(SysPost::getPostSort);
        if (StringUtils.isNotEmpty(post.getPostCode())) {
            queryWrapper.like(SysPost::getPostCode, post.getPostCode());
        }
        if (StringUtils.isNotEmpty(post.getStatus())) {
            queryWrapper.eq(SysPost::getStatus, post.getStatus());
        }
        if (StringUtils.isNotEmpty(post.getPostName())) {
            queryWrapper.like(SysPost::getPostName, post.getPostName());
        }
        return queryWrapper;
    }

    /**
     * 查询岗位信息集合
     * 
     * @param post 岗位信息
     * @return 岗位信息集合
     */
    @Override
    public List<SysPost> selectPostList(SysPost post) {
        return postMapper.selectList(buildPostQuery(post));
    }

    /**
     * 查询所有岗位
     * 
     * @return 岗位列表
     */
    @Override
    public List<SysPost> selectPostAll() {
        return postMapper.selectList(new LambdaQueryWrapper<SysPost>().orderByAsc(SysPost::getPostSort));
    }

    /**
     * 通过岗位ID查询岗位信息
     * 
     * @param postId 岗位ID
     * @return 角色对象信息
     */
    @Override
    public SysPost selectPostById(Long postId) {
        return postMapper.selectById(postId);
    }

    /**
     * 根据用户ID获取岗位选择框列表
     * 
     * @param userId 用户ID
     * @return 选中岗位ID列表
     */
    @Override
    public List<Long> selectPostListByUserId(Long userId) {
        return postMapper.selectPostListByUserId(userId);
    }

    /**
     * 校验岗位名称是否唯一
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public String checkPostNameUnique(SysPost post) {
        Long postId = StringUtils.isNull(post.getId()) ? -1L : post.getId();
        SysPost info = postMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostName, post.getPostName())
                .last("limit 1"));
        if (StringUtils.isNotNull(info) && info.getId().longValue() != postId.longValue()) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验岗位编码是否唯一
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public String checkPostCodeUnique(SysPost post) {
        Long postId = StringUtils.isNull(post.getId()) ? -1L : post.getId();
        SysPost info = postMapper.selectOne(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getPostCode, post.getPostCode())
                .last("limit 1"));
        if (StringUtils.isNotNull(info) && info.getId().longValue() != postId.longValue()) {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 通过岗位ID查询岗位使用数量
     * 
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public int countUserPostById(Long postId) {
        return userPostMapper.countUserPostById(postId);
    }

    /**
     * 删除岗位信息
     * 
     * @param postId 岗位ID
     * @return 结果
     */
    @Override
    public int deletePostById(Long postId) {
        return postMapper.deleteById(postId);
    }

    /**
     * 批量删除岗位信息
     * 
     * @param postIds 需要删除的岗位ID
     * @return 结果
     */
    @Override
    public int deletePostByIds(Long[] postIds) {
        for (Long postId : postIds) {
            SysPost post = selectPostById(postId);
            if (countUserPostById(postId) > 0) {
                throw new ServiceException(String.format("%1$s已分配,不能删除", post.getPostName()));
            }
        }
        int rows = 0;
        for (Long postId : postIds) {
            rows += postMapper.deleteById(postId);
        }
        return rows;
    }

    /**
     * 新增保存岗位信息
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public int insertPost(SysPost post) {
        post.setId(GeneratorUtil.getNextId());
        return postMapper.insertPost(post);
    }

    /**
     * 修改保存岗位信息
     * 
     * @param post 岗位信息
     * @return 结果
     */
    @Override
    public int updatePost(SysPost post) {
        return postMapper.updatePost(post);
    }
}
