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
  private final ConcurrentHashMap<String, ConcurrentHashMap<String, EventInfo>> registeredClasses;

  private static class EventInfo {
    private final String name;
    private final int eventMask;

    public EventInfo(String name, int eventMask) {
      this.name = name;
      this.eventMask = eventMask;
    }

    public String getName() {
      return name;
    }
  }

  public boolean isRegistered(String className) {
    return registeredClasses.get(className) != null;
  }

  public MethodMapping() {
    registeredClasses = new ConcurrentHashMap<String, ConcurrentHashMap<String, EventInfo> >(10);
  }

  private Map<String, EventInfo> getRegisteredMethods(String className) {
    return registeredClasses.get(className);
  }

  public synchronized void register(String className, String methodName, String eventMethod) {
    ConcurrentHashMap<String, EventInfo> methods = registeredClasses.get(className);
    if (null == methods) {
      methods = new ConcurrentHashMap<String, EventInfo>(2);
      registeredClasses.put(className, methods);
    }
    methods.put(methodName, new EventInfo(eventMethod, 0));
  }

  public Set<String> registeredClassNames() {
    return registeredClasses.keySet();
  }

  public String getTargetFor(String className, String name) {
    Map<String, EventInfo> map = registeredClasses.get(className);
    if (map == null) {
      return null;
    }
    EventInfo targetInfo = map.get(name);
    if (targetInfo == null) {
      return null;
    }
    return targetInfo.getName();
  }
}
