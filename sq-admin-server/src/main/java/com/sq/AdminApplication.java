package com.sq;

import com.sq.common.core.domain.entity.SysDictData;
import com.sq.common.utils.DictUtils;
import com.sq.core.crypto.JceUnlimitedPolicy;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 启动程序
 *
 * @author tzt
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class AdminApplication {
    public static void main(String[] args) {
        JceUnlimitedPolicy.enable();
        DictUtils.setDefaultDictDataClass(SysDictData.class);
        SpringApplication.run(AdminApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  项目启动成功   ლ(´ڡ`ლ)");
    }
}
