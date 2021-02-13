/**
 *    Copyright 2009-2020 the original author or authors.
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
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * 二级缓存
 * 跨事务缓存
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  private final Cache delegate;
  /**
   * 初始为 false
   * 提交后为 true
   */
  private boolean clearOnCommit;
  /**
   * 事务提交之后需要保存的 k-v
   */
  private final Map<Object, Object> entriesToAddOnCommit;
  /**
   * 记录查询过程中 miss 的 key
   */
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 查找对象
   * 查不到缓存到 entriesMissedInCache 中
   * 查到则
   */
  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key);
    // 缓存中没有该 key
    if (object == null) {
      // 保存起来
      entriesMissedInCache.add(key);
    }
    // issue #146
    // 如果处于提交后的状态，则返回 null
    // 表示找不到缓存
    if (clearOnCommit) {
      return null;
    } else {
      // 返回查到的缓存
      return object;
    }
  }

  @Override
  public void putObject(Object key, Object object) {
    // 添加到临时的 entriesToAddOnCommit 中
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * 事务提交
   */
  public void commit() {
    if (clearOnCommit) {
      delegate.clear();
    }
    flushPendingEntries();
    reset();
  }

  /**
   * 事务回滚
   */
  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  /**
   * 重置
   */
  private void reset() {
    clearOnCommit = false;
    // 清空
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 只有 commit 的时候会调用该方法
   * 处理临时保存的 entriesToAddOnCommit 与 entriesMissedInCache
   */
  private void flushPendingEntries() {
    // 将临时保存在 entriesToAddOnCommit 中的 k-v 缓存到 delegate 中
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    // 将临时保存在 entriesMissedInCache 中的 key 也保存到 delegate 中，但是 value 为 null
    // 为什么需要保存到 delegate 缓存中？ 因为避免大量的缓存穿透，导致对数据库的压力很大
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        // 缓存空对象
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 如果事务回滚，需要清空 delegate 中此次事务产生的 k-v 缓存
   */
  private void unlockMissedEntries() {
    // 遍历未命中的缓存
    for (Object entry : entriesMissedInCache) {
      try {
        // 移除
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}
