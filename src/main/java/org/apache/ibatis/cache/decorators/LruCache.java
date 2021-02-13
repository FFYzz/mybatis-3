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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.ibatis.cache.Cache;

/**
 * 装饰者模式的实践
 * 虽然是 lru，但是并没有从 keyMap 中移除元素
 * Lru (least recently used) cache decorator.
 *
 * @author Clinton Begin
 */
public class LruCache implements Cache {

  /**
   * 持有一个 Cache
   */
  private final Cache delegate;
  private Map<Object, Object> keyMap;
  /**
   * 保存最老的元素
   */
  private Object eldestKey;

  /**
   * 构造函数需要传入一个 Cache
   * @param delegate
   */
  public LruCache(Cache delegate) {
    this.delegate = delegate;
    // 设置 lru 缓存能够存储的元素的个数
    // 默认为 1024 个
    setSize(1024);
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(final int size) {
    // 创建一个 LinkedHashMap
    // 传入 true accessOrder
    keyMap = new LinkedHashMap<Object, Object>(size, .75F, true) {
      private static final long serialVersionUID = 4267176411845948333L;

      /**
       * 重写 removeEldestEntry
       * 不删除元素，而是取出最老的元素，保存到 eldestKey 中
       * 在 map 的 put 操作之后会调用该方法
       * 队头 head 是最老的元素
       * 队尾 tail 是最近操作过的元素
       * @param eldest
       * @return
       */
      @Override
      protected boolean removeEldestEntry(Map.Entry<Object, Object> eldest) {
        // 判断是否擦好处阈值
        boolean tooBig = size() > size;
        // 判断是否超出
        if (tooBig) {
          eldestKey = eldest.getKey();
        }
        return tooBig;
      }
    };
  }

  @Override
  public void putObject(Object key, Object value) {
    // 委托给 delegate 处理
    delegate.putObject(key, value);
    cycleKeyList(key);
  }

  @Override
  public Object getObject(Object key) {
    // 访问一下 key，会保存到链表的最前面
    keyMap.get(key); // touch
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyMap.clear();
  }

  private void cycleKeyList(Object key) {
    // 调用 LinkedHashMap 的 put 方法，存放进去一个 key
    keyMap.put(key, key);
    if (eldestKey != null) {
      delegate.removeObject(eldestKey);
      eldestKey = null;
    }
  }

}
