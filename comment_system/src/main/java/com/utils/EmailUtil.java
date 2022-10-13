package com.utils;

public class EmailUtil {

    public static final String EMAIL_SUBJECT = "美团验证码";

    public static final String EMAIL_TEMPLATE = "您的验证码为：%s，请妥善保管，不要告诉他人！";

    public static String format(String code){

        return String.format(EMAIL_TEMPLATE,code);
    }
}
