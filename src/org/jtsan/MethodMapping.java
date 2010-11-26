/* Copyright (c) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jtsan;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Keeps a mapping of system methods to their interception handlers.
 *
 * @author Egor Pasko
 */
public class MethodMapping {
  public static final int E_NONE = 0;
  public static final int E_BEFORE_METHOD = 1;
  public static final int E_AFTER_METHOD = 2;
  public static final int E_EXCEPTION = 3;
  public static final int E_BEFORE_STATIC_METHOD = 4;
  public static final int E_AFTER_STATIC_METHOD = 5;
  public static final int E_STATIC_EXCEPTION = 6;


  private final ConcurrentMap<EventInfo, LinkedList<HandlerInfo>> map =
      new ConcurrentHashMap<EventInfo, LinkedList<HandlerInfo>>(10);
  private final Set<String> benignRaceFields = new HashSet<String>();

  /**
   * Keeps information about target handler method and the source class that
   * matches to it.
   */
  public static final class HandlerInfo {
    private final String handler;
    private final String watchedClass;
    private final boolean exact;

    public HandlerInfo(String cls, String handler, boolean exact) {
      this.handler = handler;
      this.watchedClass = cls;
      this.exact = exact;
    }

    public boolean isExact() {
      return exact;
    }

    public String getHandler() {
      return handler;
    }

    public String getWatchedClass() {
      return watchedClass;
    }
  }

  /**
   * A hashable tuple of (methodName, eventType).
   */
  private static class EventInfo {
    private final String methodName;
    private final int eventType;
    private final int hash;

    public EventInfo(String meth, int type) {
      this.methodName = meth;
      this.eventType = type;
      this.hash = calculateHashCode();
    }

    private int calculateHashCode() {
      int h = methodName == null ? 3 : methodName.hashCode();
      h = 7 * h + 5 * eventType;
      return h;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof EventInfo)) {
        return false;
      }
      EventInfo e = (EventInfo)obj;
      return (methodName != null &&
          methodName.equals(e.methodName) &&
          e.eventType == eventType);
    }

    public int hashCode() {
      return hash;
    }
  }

  public synchronized void registerEvent(
      String className, String methodName, int eventType, String eventMethod, boolean exact) {
    EventInfo ei = new EventInfo(methodName, eventType);
    LinkedList<HandlerInfo> lst = map.get(ei);
    HandlerInfo handler = new HandlerInfo(className, eventMethod, exact);
    if (lst == null) {
      lst = new LinkedList<HandlerInfo>();
      map.put(ei, lst);
    }
    lst.addLast(handler);
  }

  public void registerBefore(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_BEFORE_METHOD, eventMethod, false /* exact */);
  }

  public void registerBeforeExact(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_BEFORE_METHOD, eventMethod, true /* exact */);
  }

  public void registerBeforeStatic(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_BEFORE_STATIC_METHOD, eventMethod, false /* exact */);
  }

  public void registerBeforeStaticExact(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_BEFORE_STATIC_METHOD, eventMethod, true /* exact */);
  }

  public void registerAfter(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_AFTER_METHOD, eventMethod, false /* exact */);
  }

  public void registerAfterExact(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_AFTER_METHOD, eventMethod, true /* exact */);
  }

  public void registerAfterStatic(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_AFTER_STATIC_METHOD, eventMethod, false /* exact */);
  }

  public void registerAfterStaticExact(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_AFTER_STATIC_METHOD, eventMethod, true /* exact */);
  }

  public void registerException(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_EXCEPTION, eventMethod, false /* exact */);
  }

  public void registerExceptionStatic(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_STATIC_EXCEPTION, eventMethod, false /* exact */);
  }

  public List<HandlerInfo> getTargetsFor(String name, int eventType) {
    return map.get(new EventInfo(name, eventType));
  }

  public synchronized void benignRaceField(String clazz, String field) {
    benignRaceFields.add(clazz + "." + field);
  }

  public synchronized boolean isBenignRaceField(String clazz, String field) {
    return benignRaceFields.contains(clazz + "." + field);
  }
}
