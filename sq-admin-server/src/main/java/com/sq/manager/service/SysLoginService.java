package com.sq.manager.service;

import com.sq.common.constant.CacheConstants;
import com.sq.common.constant.Constants;
import com.sq.common.core.domain.entity.SysUser;
import com.sq.common.core.domain.model.LoginUser;
import com.sq.common.core.redis.RedisCache;
import com.sq.common.exception.ServiceException;
import com.sq.common.exception.user.CaptchaExpireException;
import com.sq.common.exception.user.UserPasswordNotMatchException;
import com.sq.common.utils.DateUtils;
import com.sq.common.utils.MessageUtils;
import com.sq.common.utils.ServletUtils;
import com.sq.common.utils.StringUtils;
import com.sq.common.utils.ip.IpUtils;
import com.sq.framework.security.context.AuthenticationContextHolder;
import com.sq.framework.web.service.TokenService;
import com.sq.manager.AsyncManager;
import com.sq.manager.factory.AsyncFactory;
import com.sq.system.service.ISysConfigService;
import com.sq.system.service.ISysUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 登录校验方法
 *
 * @author tzt
 */
@Slf4j
@Component
public class SysLoginService {
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private Sm2PasswordService sm2PasswordService;

    /**
     * 登录验证
     *
     * @param username 用户名
     * @param password 密码（前端 sm-crypto SM2 加密后的 hex 密文）
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public String login(String username, String password, String code, String uuid) {
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        // 验证码开关
        if (captchaEnabled) {
            validateCaptcha(username, code, uuid);
        }
        // SM2 解密前端密文密码
        String plainPassword = sm2PasswordService.decryptPassword(password);
        // 用户验证
        Authentication authentication = null;
        try {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, plainPassword);
            AuthenticationContextHolder.setContext(authenticationToken);
            // 该方法会去调用UserDetailsServiceImpl.loadUserByUsername
            authentication = authenticationManager.authenticate(authenticationToken);
        }
        catch (Exception e) {
            if (e instanceof BadCredentialsException) {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
                log.error("登录异常",e);
                throw new UserPasswordNotMatchException();
            }
            else {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, e.getMessage()));
                log.error("登录异常",e);
                throw new ServiceException(e.getMessage());
            }
        }
        finally {
            AuthenticationContextHolder.clearContext();
        }
        SecurityContextHolder.getContext().setAuthentication(authentication);
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        recordLoginInfo(loginUser.getUserId());
        // 生成token
        return tokenService.createToken(loginUser);
    }

    /**
     * 校验验证码
     *
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public void validateCaptcha(String username, String code, String uuid) {
        String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
        String captcha = redisCache.getCacheObject(verifyKey);
        redisCache.deleteObject(verifyKey);
        if (captcha == null) {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire")));
            throw new CaptchaExpireException();
        }
        if (!code.equalsIgnoreCase(captcha)) {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error")));
            throw new ServiceException("验证码错误！");
        }
    }

    /**
     * 记录登录信息
     *
     * @param userId 用户ID
     */
    public void recordLoginInfo(Long userId) {
        SysUser sysUser = new SysUser();
        sysUser.setId(userId);
        sysUser.setLoginIp(IpUtils.getIpAddr(ServletUtils.getRequest()));
        sysUser.setLoginDate(DateUtils.getNowDate());
        userService.updateUserProfile(sysUser);
    }
}
