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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Deque;
import java.util.LinkedList;

import org.apache.ibatis.cache.Cache;

/**
 * Weak Reference cache decorator.
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 */
public class WeakCache implements Cache {
  /**
   * 强引用的对象 list，避免被 gc 回收
   * FILO 的队列
   *
   * linkedlist
   */
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  /**
   * 引用队列，在被回收时会进入引用队列
   */
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  /**
   * 被装饰的 Cache
   */
  private final Cache delegate;
  /**
   * 强引用对象的个数
   */
  private int numberOfHardLinks;

  public WeakCache(Cache delegate) {
    this.delegate = delegate;
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }

  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    removeGarbageCollectedItems();
    // value 是一个 WeakEntry
    // 当 value 被收拾会收到一个通知
    // delegate 中保存的是一个 WeakReference 指向的对象
    delegate.putObject(key, new WeakEntry(key, value, queueOfGarbageCollectedEntries));
  }

  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
      // 弱引用
    WeakReference<Object> weakReference = (WeakReference<Object>) delegate.getObject(key);
    if (weakReference != null) {
      // 获取目标 Object
      result = weakReference.get();
      // 为空，说明被回收了
      if (result == null) {
        // delegate 中也删除
        delegate.removeObject(key);
      } else {
        // 未被回收
        // 强引用指向该对象
        // 这样该 key 对应的 object 就不会被回收
        // 添加到队头
        // FILO
        hardLinksToAvoidGarbageCollection.addFirst(result);
        if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
          // 移除队尾元素
          hardLinksToAvoidGarbageCollection.removeLast();
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    hardLinksToAvoidGarbageCollection.clear();
    removeGarbageCollectedItems();
    delegate.clear();
  }

  /**
   * 基本上每个操作都会去检查一下是否有 value 被回收
   */
  private void removeGarbageCollectedItems() {
    WeakEntry sv;
    while ((sv = (WeakEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      // 将 delegate 中保存的 缓存清除
      delegate.removeObject(sv.key);
    }
  }

  /**
   * 弱引用
   */
  private static class WeakEntry extends WeakReference<Object> {
    private final Object key;
    private WeakEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue);
      this.key = key;
    }
  }

}
