package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizJournalPage;
import com.sq.bus.mapper.BizJournalPageMapper;
import com.sq.bus.service.IBizJournalPageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class BizJournalPageServiceImpl extends ServiceImpl<BizJournalPageMapper, BizJournalPage>
        implements IBizJournalPageService {

    @Override
    public List<BizJournalPage> listByRange(Long bookId, int from, int to) {
        if (bookId == null || to < from) {
            return Collections.emptyList();
        }
        return list(new LambdaQueryWrapper<BizJournalPage>()
                .eq(BizJournalPage::getBookId, bookId)
                .ge(BizJournalPage::getPageNo, from)
                .le(BizJournalPage::getPageNo, to)
                .orderByAsc(BizJournalPage::getPageNo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizJournalPage upsertContent(Long bookId, Integer pageNo, String contentJson, String username) {
        BizJournalPage page = getOne(new LambdaQueryWrapper<BizJournalPage>()
                .eq(BizJournalPage::getBookId, bookId)
                .eq(BizJournalPage::getPageNo, pageNo)
                .last("LIMIT 1"));
        Date now = new Date();
        if (page == null) {
            page = new BizJournalPage();
            page.setBookId(bookId);
            page.setPageNo(pageNo);
            page.setContentJson(contentJson);
            page.setCreateBy(username);
            page.setCreateTime(now);
            page.setUpdateBy(username);
            page.setUpdateTime(now);
            save(page);
            return page;
        }
        page.setContentJson(contentJson);
        page.setUpdateBy(username);
        page.setUpdateTime(now);
        updateById(page);
        return page;
    }
}
