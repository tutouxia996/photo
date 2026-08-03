package com.sq.common.utils;

import com.sq.common.exception.ServiceException;

public class PasswordValidator {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 20;

    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ServiceException("密码长度不能小于" + MIN_LENGTH + "位");
        }
        if (password.length() > MAX_LENGTH) {
            throw new ServiceException("密码长度不能大于" + MAX_LENGTH + "位");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new ServiceException("密码必须包含大写字母");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new ServiceException("密码必须包含小写字母");
        }
        if (!password.matches(".*\\d.*")) {
            throw new ServiceException("密码必须包含数字");
        }
        if (!password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            throw new ServiceException("密码必须包含至少一个特殊字符");
        }
    }
}
