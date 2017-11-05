package com.sm.finance.charge.storage.api;

import com.sm.finance.charge.common.base.Closable;
import com.sm.finance.charge.common.base.Startable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * @author shifeng.luo
 * @version created on 2017/9/25 下午10:41
 */
public interface StorageAppender extends Startable, Closable {

    /**
     * 同步写数据，此时数据只会写入操作系统内存，不会强制刷盘
     *
     * @param message 数据
     */
    boolean append(Object message);

    /**
     * 同步批量写数据，此时数据只会写入操作系统内存，不会强制刷盘
     *
     * @param messages 数据
     */
    boolean append(List<?> messages);

    /**
     * 同步且强制刷盘写数据，此时数据不仅会写入操作系统内存，并且会同时强制刷盘
     *
     * @param message 数据
     */
    boolean appendForce(Object message);

    /**
     * 同步且强制刷盘批量写数据，此时数据不仅会写入操作系统内存，并且会同时强制刷盘
     *
     * @param messages 数据
     */
    boolean appendForce(List<?> messages);

    /**
     * 异步写数据，此时数据只会写入操作系统内存，不会强制刷盘
     *
     * @param message 数据
     */
    CompletableFuture<Boolean> appendAsync(Object message);

    /**
     * 异步写数据，但是此时数据只会写入操作系统内存，不会强制刷盘
     *
     * @param messages 数据
     */
    CompletableFuture<Boolean> appendAsync(List<?> messages);
}