/*-
 * -\-\-
 * Spotify Styx Scheduler
 * --
 * Copyright (C) 2018 Spotify AB
 * --
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -/-/-
 */

package com.spotify.styx.state.handlers;

import static com.spotify.styx.state.RunState.State.DONE;
import static com.spotify.styx.state.RunState.State.ERROR;
import static com.spotify.styx.state.RunState.State.FAILED;
import static com.spotify.styx.state.RunState.State.PREPARE;
import static com.spotify.styx.state.RunState.State.QUEUED;
import static com.spotify.styx.state.RunState.State.RUNNING;
import static com.spotify.styx.state.RunState.State.SUBMITTED;
import static com.spotify.styx.state.RunState.State.SUBMITTING;
import static com.spotify.styx.state.RunState.State.TERMINATED;
import static com.spotify.styx.state.handlers.TransitionLogger.stateInfo;
import static java.lang.String.format;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.spotify.styx.model.WorkflowId;
import com.spotify.styx.model.WorkflowInstance;
import com.spotify.styx.state.RunState;
import com.spotify.styx.state.StateData;
import org.junit.Test;

public class TransitionLoggerTest {

  private static final WorkflowInstance WORKFLOW_INSTANCE =
      WorkflowInstance.create(WorkflowId.create("foo", "bar"), "2018-04-17");

  @Test
  public void shouldReturnTries() {
    assertThat(stateInfo(RunState.fresh(WORKFLOW_INSTANCE)), is("tries:0"));
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, PREPARE)),
        is("tries:0"));
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, ERROR)),
        is("tries:0"));
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, DONE)),
        is("tries:0"));
  }

  @Test
  public void shouldReturnTriesAndExecId() {
    final StateData stateData = StateData.newBuilder().executionId("exec-1").build();
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, SUBMITTED, stateData)),
        is(format("tries:0 execId:%s", stateData.executionId())));
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, RUNNING, stateData)),
        is(format("tries:0 execId:%s", stateData.executionId())));
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, FAILED, stateData)),
        is(format("tries:0 execId:%s", stateData.executionId())));
  }

  @Test
  public void shouldReturnTriesExecIdAndExitCode() {
    final StateData stateData = StateData.newBuilder().executionId("exec-1").build();
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, TERMINATED, stateData)),
        is(format("tries:0 execId:%s exitCode:-", stateData.executionId())));
    assertThat(stateInfo(
        RunState.create(WORKFLOW_INSTANCE, TERMINATED, stateData.builder().lastExit(10).build())),
        is(format("tries:0 execId:%s exitCode:10", stateData.executionId())));
  }

  @Test
  public void shouldReturnTriesAndDelayMs() {
    final StateData stateData =
        StateData.newBuilder().executionId("exec-1").retryDelayMillis(1000L).build();
    assertThat(stateInfo(
        RunState.create(WORKFLOW_INSTANCE, QUEUED, stateData)),
        is(format("tries:0 delayMs:%s", stateData.retryDelayMillis())));
  }
  
  @Test
  public void shouldReturnEmptyString() {
    assertThat(stateInfo(RunState.create(WORKFLOW_INSTANCE, SUBMITTING)), is(""));
  }
}
