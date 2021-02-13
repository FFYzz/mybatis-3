/**
 *    Copyright 2009-2019 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * 二级缓存，跨事务，跨 sqlSession 时缓存的处理
 * 事务缓存管理器，管理多个 TransactionalCache
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  /**
   * 保存缓存以及其对应的 TransactionalCache
   */
  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  /**
   * 清除所有的缓存
   */
  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  public Object getObject(Cache cache, CacheKey key) {
    // 获取 Cache 对应的 TransactionalCache，随后读取缓存
    return getTransactionalCache(cache).getObject(key);
  }

  public void putObject(Cache cache, CacheKey key, Object value) {
    // 获取 Cache 对应的 TransactionalCache，随后放入缓存
    getTransactionalCache(cache).putObject(key, value);
  }

  /**
   * 事务提交
   */
  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  /**
   * 事务回滚
   */
  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  /**
   * 返回一个 TransactionalCache
   */
  private TransactionalCache getTransactionalCache(Cache cache) {
    // 返回一个 TransactionalCache
    // 如果不存在则创建一个
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}
