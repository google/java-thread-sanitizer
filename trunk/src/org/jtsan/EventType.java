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

/**
 *
 * All supported events.
 *
 * @author Sergey Vorobyev
 */
public enum EventType {

  // Tsan offline events.
  EXPECT_RACE_BEGIN,
  EXPECT_RACE_END,
  READ,
  READER_LOCK,
  RTN_CALL,
  RTN_EXIT,
  SBLOCK_ENTER,
  SIGNAL,
  STACK_TRACE,
  THR_END,
  THR_FIRST_INSN,
  THR_JOIN_AFTER,
  THR_START,
  UNLOCK,
  WAIT,
  WRITE,
  WRITER_LOCK,

  // Comments, PC events for binary format.
  COMMENT,
  CODE_POSITION,
  ;

  private EventType() {
  }

}
