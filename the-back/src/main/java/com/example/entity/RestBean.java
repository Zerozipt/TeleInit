package com.example.entity;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

/**
 * 用于封装API响应的实体类。
 * @param <T> 响应数据类型
 */
public record RestBean<T>(int code, T data, String message) {
    /**
     * 创建一个成功的响应。
     * @param data 响应数据
     * @param <T> 响应数据类型
     * @return 成功的RestBean实例
     */
    public static <T> RestBean<T> success(T data) {

        return new RestBean<>(200, data, "success");
    }

    /**
     * 创建一个成功的响应（无数据）。
     * @param <T> 响应数据类型
     * @return 成功的RestBean实例
     */
    public static <T> RestBean<T> success() {
        return success(null);
    }

    /**
     * 创建一个未授权的响应。
     * @param message 错误消息
     * @param <T> 响应数据类型
     * @return 未授权的RestBean实例
     */
    public static <T> RestBean<T> unauthorized(String message) {
        return failure(401, message);
    }

    /**
     * 创建一个禁止访问的响应。
     * @param message 错误消息
     * @param <T> 响应数据类型
     * @return 禁止访问的RestBean实例
     */
    public static <T> RestBean<T> forbidden(String message) {
        return failure(401, message);
    }
    /**
     * 创建一个失败的响应。
     * @param code 状态码
     * @param message 错误消息
     * @param <T> 响应数据类型
     * @return 失败的RestBean实例
     */
    public static <T> RestBean<T> failure(int code, String message) {
        return new RestBean<>(code, null, message);
    }

    /**
     * 将RestBean实例转换为JSON字符串。
     * @return JSON字符串
     */
    public String asJsonString() {
        return JSONObject.toJSONString(this, JSONWriter.Feature.WriteNulls);
    }
}