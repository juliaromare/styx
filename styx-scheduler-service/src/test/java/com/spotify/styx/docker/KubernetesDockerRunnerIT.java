/*
 * -\-\-
 * Spotify Styx Scheduler Service
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

package com.spotify.styx.docker;

import static com.spotify.styx.state.RunState.State.RUNNING;
import static org.mockito.Mockito.mock;

import com.google.common.collect.ImmutableList;
import com.spotify.styx.docker.DockerRunner.RunSpec;
import com.spotify.styx.docker.KubernetesDockerRunner.KubernetesSecretSpec;
import com.spotify.styx.model.Event;
import com.spotify.styx.model.WorkflowId;
import com.spotify.styx.model.WorkflowInstance;
import com.spotify.styx.monitoring.Stats;
import com.spotify.styx.state.RunState;
import com.spotify.styx.state.RunState.State;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.Watcher.Action;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

public class KubernetesDockerRunnerIT {

  private DefaultKubernetesClient kubernetesClient;

  @Before
  public void setUp() throws Exception {

    final Config kubeConfig = new ConfigBuilder()
        .withMasterUrl("127.0.0.1:8001")
        .withNamespace("default")
        .build();

    kubernetesClient = new DefaultKubernetesClient(kubeConfig);

  }

  @Test
  public void testCreatePod() throws InterruptedException, IOException {
    final String podName = UUID.randomUUID().toString();
    final RunSpec runSpec = RunSpec.builder()
        .executionId(podName)
        .imageName("busybox")
        .args("/bin/sh", "-c", "while :; do sleep 1; echo hello; done")
        .build();
    final WorkflowId workflowId = WorkflowId.create("foo", "bar");
    final WorkflowInstance wfi = WorkflowInstance.create(workflowId, "baz");
    final KubernetesSecretSpec kubernetesSecretSpec = KubernetesSecretSpec.builder().build();
    final Pod pod = KubernetesDockerRunner.createPod(wfi, runSpec, kubernetesSecretSpec);
    kubernetesClient.pods().create(pod);

    while (true) {
      System.err.println();
      System.err.println("====================");
      System.err.println();

      for (State state : ImmutableList.of(State.SUBMITTED, RUNNING)) {

        System.err.println("Events from " + state + ": ");

        final RunState runState = RunState.create(wfi, state);
        final Pod createdPod = kubernetesClient.pods().withName(podName).get();
        final List<Event> events = KubernetesPodEventTranslator
            .translate(wfi, runState, Action.MODIFIED, createdPod, mock(Stats.class));

        events.forEach(System.err::println);

        System.err.println();
      }

      Thread.sleep(2000);

    }

  }
}
