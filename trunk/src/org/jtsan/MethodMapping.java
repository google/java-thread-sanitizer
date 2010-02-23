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

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Keeps a mapping of system methods to their interception handlers.
 *
 * @author Egor Pasko
 */
public class MethodMapping {
  public static final int E_NONE = 0;
  public static final int E_BEFORE_METHOD = 1;
  public static final int E_AFTER_METHOD = 2;

  private final ConcurrentHashMap<EventInfo, String> map;

  /**
   * A hashable tuple of (className, methodName, eventType).
   */
  private static class EventInfo {
    private final String className;
    private final String methodName;
    private final int eventType;
    private final int hash;

    public EventInfo(String cls, String meth, int type) {
      this.className = cls;
      this.methodName = meth;
      this.eventType = type;
      this.hash = calculateHashCode();
    }

    private int calculateHashCode() {
      int h = className == null ? 2 : className.hashCode();
      h = 31 * h + (methodName == null ? 3 : methodName.hashCode());
      h = 7 * h + 5 * eventType;
      return h;
    }

    public boolean equals(Object obj) {
      if (!(obj instanceof EventInfo)) {
        return false;
      }
      EventInfo e = (EventInfo)obj;
      return (className != null &&
          className.equals(e.className) &&
          methodName != null &&
          methodName.equals(e.methodName) &&
          e.eventType == eventType);
    }

    public int hashCode() {
      return hash;
    }
  }

  public MethodMapping() {
    map = new ConcurrentHashMap<EventInfo, String>(10);
  }

  public void registerEvent(
      String className, String methodName, int eventType, String eventMethod) {
    map.put(new EventInfo(className, methodName, eventType), eventMethod);
  }

  public void registerBefore(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_BEFORE_METHOD, eventMethod);
  }

  public void registerAfter(String className, String methodName, String eventMethod) {
    registerEvent(className, methodName, E_AFTER_METHOD, eventMethod);
  }

  public String getTargetFor(String className, String name, int eventType) {
    return map.get(new EventInfo(className, name, eventType));

  }
}
