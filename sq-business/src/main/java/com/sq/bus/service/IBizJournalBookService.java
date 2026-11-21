package com.sq.bus.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sq.bus.domain.BizJournalBook;

public interface IBizJournalBookService extends IService<BizJournalBook> {

    /**
     * 获取当前用户默认日记本；不存在则创建。
     */
    BizJournalBook getOrCreateMine(String username);
}
