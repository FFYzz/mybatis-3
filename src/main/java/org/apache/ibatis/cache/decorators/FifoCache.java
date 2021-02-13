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
package org.apache.ibatis.cache.decorators;

import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * FIFO 的有限缓存的队列
 * 使用额外的 Linkedlist 保存
 * FIFO (first in, first out) cache decorator.
 *
 * @author Clinton Begin
 */
public class FifoCache implements Cache {

  /**
   * 被装饰的 Cache
   */
  private final Cache delegate;
  /**
   * key 保存队列
   */
  private final Deque<Object> keyList;
  /**
   * 缓存能存放的最多的元素个数
   */
  private int size;

  public FifoCache(Cache delegate) {
    this.delegate = delegate;
    this.keyList = new LinkedList<>();
    this.size = 1024;
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.size = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    // 放入的时候需要放到 map 与 list 中
    cycleKeyList(key);
    delegate.putObject(key, value);
  }

  @Override
  public Object getObject(Object key) {
    return delegate.getObject(key);
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
    keyList.clear();
  }

  private void cycleKeyList(Object key) {
    keyList.addLast(key);
    // 如果超过了设置的最多的个数
    if (keyList.size() > size) {
      // 移除队头元素
      Object oldestKey = keyList.removeFirst();
      // delegate 缓存同样需要移除
      delegate.removeObject(oldestKey);
    }
  }

}
