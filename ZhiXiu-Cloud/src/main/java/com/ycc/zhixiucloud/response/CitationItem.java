package com.ycc.zhixiucloud.response;

/** 知识 ID 以字符串传输，避免前端长整数精度丢失。 */
public record CitationItem(String id, String title) {
}
