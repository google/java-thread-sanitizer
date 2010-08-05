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
  EXPECT_RACE_BEGIN,  // tid pc 0 0
  EXPECT_RACE_END,    // tid pc 0 0
  READ,               // tid pc id 1
  READER_LOCK,        // tid pc id 0
  RTN_CALL,           // tid 0 0 0
  RTN_EXIT,           // tid pc 0 0
  SBLOCK_ENTER,       // tid pc 0 0
  SIGNAL,             // tid pc id 0
  STACK_TRACE,        // tid pc 0 0
  THR_END,            // tid pc 0 0
  THR_FIRST_INSN,     // tid pc 0 0
  THR_JOIN_AFTER,     // tid pc tid 0
  THR_START,          // tid pc 0 tid
  UNLOCK,             // tid pc id 0
  WAIT,               // tid pc id 0
  WRITE,              // tid pc id 1
  WRITER_LOCK,        // tid pc id 0

  // Comments, PC events for binary format.
  COMMENT, // strsize str
  CODE_POSITION, // pc strsize str
  ;

  private EventType() {
  }

}
