package com.sq.bus.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sq.bus.domain.BizJournalBook;
import com.sq.bus.mapper.BizJournalBookMapper;
import com.sq.bus.service.IBizJournalBookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;

@Service
public class BizJournalBookServiceImpl extends ServiceImpl<BizJournalBookMapper, BizJournalBook>
        implements IBizJournalBookService {

    private static final int DEFAULT_PAGE_COUNT = 1000;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BizJournalBook getOrCreateMine(String username) {
        BizJournalBook existing = getOne(new LambdaQueryWrapper<BizJournalBook>()
                .eq(BizJournalBook::getCreateBy, username)
                .orderByAsc(BizJournalBook::getBookId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        Date now = new Date();
        BizJournalBook book = new BizJournalBook();
        book.setTitle("相册日记");
        book.setCoverSub("HOBONICHI STYLE");
        book.setYear(Calendar.getInstance().get(Calendar.YEAR));
        book.setPageCount(DEFAULT_PAGE_COUNT);
        book.setCreateBy(username);
        book.setCreateTime(now);
        book.setUpdateBy(username);
        book.setUpdateTime(now);
        save(book);
        return book;
    }
}
