/*-
 * -\-\-
 * Spotify Styx Scheduler Service
 * --
 * Copyright (C) 2016 Spotify AB
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

package com.spotify.styx.api;

import static com.github.npathai.hamcrestopt.OptionalMatchers.isEmpty;
import static com.spotify.apollo.test.unit.ResponseMatchers.hasStatus;
import static com.spotify.apollo.test.unit.StatusTypeMatchers.withCode;
import static com.spotify.styx.api.SchedulerResource.BASE;
import static com.spotify.styx.serialization.Json.OBJECT_MAPPER;
import static com.spotify.styx.serialization.Json.serialize;
import static com.spotify.styx.testdata.TestData.FULL_WORKFLOW_CONFIGURATION;
import static com.spotify.styx.testdata.TestData.RESOURCE_IDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.spotify.apollo.Response;
import com.spotify.apollo.Status;
import com.spotify.apollo.StatusType;
import com.spotify.apollo.test.ServiceHelper;
import com.spotify.futures.CompletableFutures;
import com.spotify.styx.TriggerListener;
import com.spotify.styx.model.Event;
import com.spotify.styx.model.Workflow;
import com.spotify.styx.model.WorkflowConfiguration;
import com.spotify.styx.model.WorkflowConfigurationBuilder;
import com.spotify.styx.model.WorkflowId;
import com.spotify.styx.model.WorkflowInstance;
import com.spotify.styx.state.StateManager;
import com.spotify.styx.state.Trigger;
import com.spotify.styx.storage.Storage;
import com.spotify.styx.testdata.TestData;
import com.spotify.styx.util.AlreadyInitializedException;
import com.spotify.styx.util.TriggerUtil;
import com.spotify.styx.util.WorkflowValidator;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import okio.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * API endpoints for interacting directly with the scheduler
 */
public class SchedulerResourceTest {

  private static final WorkflowInstance WFI = WorkflowInstance
      .create(TestData.WORKFLOW_ID, "12345");

  private final Workflow HOURLY_WORKFLOW = Workflow.create("styx",
                                                           TestData.HOURLY_WORKFLOW_CONFIGURATION);
  private final Workflow DAILY_WORKFLOW = Workflow.create("styx",
                                                          TestData.DAILY_WORKFLOW_CONFIGURATION);
  private final Workflow FULL_DAILY_WORKFLOW = Workflow.create("styx",
                                                               FULL_WORKFLOW_CONFIGURATION);
  private final Workflow WEEKLY_WORKFLOW = Workflow.create("styx",
                                                           TestData.WEEKLY_WORKFLOW_CONFIGURATION);
  private final Workflow MONTHLY_WORKFLOW = Workflow.create("styx",
                                                            TestData.MONTHLY_WORKFLOW_CONFIGURATION);
  private Optional<Workflow> triggeredWorkflow = Optional.empty();
  private Optional<Instant> triggeredInstant = Optional.empty();

  @Mock WorkflowValidator workflowValidator;

  private final Storage storage = mock(Storage.class);

  @Captor ArgumentCaptor<Trigger> triggerCaptor;

  @Mock StateManager stateManager;
  @Mock TriggerListener triggerListener;

  @Rule
  public ServiceHelper serviceHelper;

  public SchedulerResourceTest() {
    MockitoAnnotations.initMocks(this);
    serviceHelper = getServiceHelper(stateManager, storage);
  }

  @Before
  public void setUp() throws Exception {
    when(stateManager.receive(any())).thenReturn(CompletableFuture.completedFuture(null));
    when(triggerListener.event(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(null));
    when(workflowValidator.validateWorkflow(any())).thenReturn(Collections.emptyList());
    when(workflowValidator.validateWorkflowConfiguration(any())).thenReturn(Collections.emptyList());
  }

  private ServiceHelper getServiceHelper(StateManager stateManager, Storage storage) {
    return ServiceHelper.create((environment) -> {
      final SchedulerResource schedulerResource = new SchedulerResource(
          stateManager,
          triggerListener,
          storage,
          () -> Instant.parse("2015-12-31T23:59:10.000Z"),
          workflowValidator);

      environment.routingEngine()
          .registerRoutes(schedulerResource.routes());
    }, "styx");
  }

  private Response<ByteString> requestAndWaitTriggerWorkflowInstance(WorkflowInstance toTrigger)
      throws Exception {

    ByteString eventPayload = ByteString.of(OBJECT_MAPPER.writeValueAsBytes(toTrigger));
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/trigger", eventPayload);

    return post.toCompletableFuture().get();
  }

  @Test
  public void testRetry() throws Exception {
    ByteString wfiPayload = serialize(WFI);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/retry", wfiPayload);

    post.toCompletableFuture().get(1, MINUTES); // block until done

    verify(stateManager)
        .receive(Event.retryAfter(WFI, 0L));
  }

  @Test
  public void testRetryWithDelayParameter() throws Exception {
    ByteString wfiPayload = serialize(WFI);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/retry?delay=500", wfiPayload);

    post.toCompletableFuture().get(1, MINUTES); // block until done

    verify(stateManager).receive(Event.retryAfter(WFI, 500L));
  }

  @Test
  public void testRetryWithWrongDelayParameter() throws Exception {
    ByteString wfiPayload = serialize(WFI);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/retry?delay=abc", wfiPayload);

    final Response<ByteString> response = post.toCompletableFuture().get();

    assertThat(response, hasStatus(withCode(Status.BAD_REQUEST
        .withReasonPhrase("Delay parameter could not be parsed"))));
    verify(stateManager, never()).receive(any());
  }

  @Test
  public void testHalt() throws Exception {
    ByteString wfiPayload = serialize(WFI);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/halt", wfiPayload);

    post.toCompletableFuture().get(1, MINUTES); // block until done

    verify(stateManager)
        .receive(Event.halt(WFI));
  }

  @Test
  public void testInjectDequeueEvent() throws Exception {
    Event injectedEvent = Event.dequeue(WFI, RESOURCE_IDS);
    ByteString eventPayload = serialize(injectedEvent);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/events", eventPayload);

    post.toCompletableFuture().get(1, MINUTES); // block until done

    verify(stateManager).receive(Event.retryAfter(WFI, 0L));
  }

  @Test
  public void testInjectHaltEvent() throws Exception {
    Event injectedEvent = Event.halt(WFI);
    ByteString eventPayload = serialize(injectedEvent);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/events", eventPayload);

    post.toCompletableFuture().get(1, MINUTES); // block until done

    verify(stateManager).receive(Event.halt(WFI));
  }

  @Test
  public void testInjectTimeoutEvent() throws Exception {
    Event injectedEvent = Event.timeout(WFI);
    ByteString eventPayload = serialize(injectedEvent);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/events", eventPayload);

    post.toCompletableFuture().get(1, MINUTES); // block until done

    verify(stateManager).receive(Event.timeout(WFI));
  }

  @Test
  public void shouldFailOnInjectRetryEvent() throws Exception {
    Event injectedEvent = Event.retry(WFI);
    ByteString eventPayload = serialize(injectedEvent);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/events", eventPayload);

    final Response<ByteString> response = post.toCompletableFuture().get();
    assertThat(response, hasStatus(withCode(Status.BAD_REQUEST)));
  }

  @Test
  public void injectEventShouldReturnServerErrorIfRuntimeException() throws Exception {
    assertThat(retryFailureResponse(new RuntimeException("test")), hasStatus(withCode(Status.INTERNAL_SERVER_ERROR)));
  }

  @Test
  public void injectEventShouldReturnBadRequestOnIllegalArgumentException() throws Exception {
    assertThat(retryFailureResponse(new IllegalArgumentException("badf00d!")), hasStatus(withCode(Status.BAD_REQUEST)));
  }

  @Test
  public void injectEventShouldReturnBadRequestOnIllegalStateException() throws Exception {
    assertThat(retryFailureResponse(new IllegalStateException("badf00d!")), hasStatus(withCode(Status.BAD_REQUEST)));
  }

  private Response<ByteString> retryFailureResponse(Throwable cause) throws Exception {
    when(stateManager.receive(any())).thenReturn(
        CompletableFutures.exceptionallyCompletedFuture(cause));

    ByteString eventPayload = serialize(WFI);
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/retry", eventPayload);

    return post.toCompletableFuture().get();
  }

  @Test
  public void testTriggeredWorkflowGeneratesTrigger() throws Exception {
    when(storage.workflow(HOURLY_WORKFLOW.id())).thenReturn(Optional.of(HOURLY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(HOURLY_WORKFLOW.id(), "2014-12-31T23");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);


    assertThat(response.status(), is(Status.OK));
    final Instant expectedInstant = Instant.parse("2014-12-31T23:00:00.000Z");
    verify(triggerListener).event(eq(HOURLY_WORKFLOW), triggerCaptor.capture(), eq(expectedInstant));
    assertThat(TriggerUtil.triggerType(triggerCaptor.getValue()), is("adhoc"));
    assertThat(TriggerUtil.triggerId(triggerCaptor.getValue()), startsWith("ad-hoc-cli-"));
  }

  @Test
  public void testTriggerWorkflowInstanceHourly() throws Exception {
    when(storage.workflow(HOURLY_WORKFLOW.id())).thenReturn(Optional.of(HOURLY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(HOURLY_WORKFLOW.id(), "2014-12-31T23");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(), is(Status.OK));
    final Instant expectedInstant = Instant.parse("2014-12-31T23:00:00.000Z");
    verify(triggerListener).event(eq(HOURLY_WORKFLOW), triggerCaptor.capture(), eq(expectedInstant));
  }

  @Test
  public void testTriggerWorkflowInstanceDaily() throws Exception {
    when(storage.workflow(DAILY_WORKFLOW.id())).thenReturn(Optional.of(DAILY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(DAILY_WORKFLOW.id(), "2014-12-31");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(), is(Status.OK));
    final Instant expectedInstant = Instant.parse("2014-12-31T00:00:00.00Z");
    verify(triggerListener).event(eq(DAILY_WORKFLOW), triggerCaptor.capture(), eq(expectedInstant));
  }

  @Test
  public void testTriggerWorkflowInstanceWeekly() throws Exception {
    when(storage.workflow(WEEKLY_WORKFLOW.id())).thenReturn(Optional.of(WEEKLY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(WEEKLY_WORKFLOW.id(), "2016-01-03");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(), is(Status.OK));
    final Instant expectedInstant = Instant.parse("2015-12-28T00:00:00.000Z");
    verify(triggerListener).event(eq(WEEKLY_WORKFLOW), triggerCaptor.capture(), eq(expectedInstant));
  }

  @Test
  public void testTriggerWorkflowInstanceMissingStorage() throws Exception {
    when(storage.workflow(any())).thenReturn(Optional.empty());

    WorkflowInstance toTrigger = WorkflowInstance.create(HOURLY_WORKFLOW.id(), "2016-12-31T23");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(),
               is(Status.BAD_REQUEST.withReasonPhrase("The specified workflow is not"
                                                      + " found in the scheduler")));

    verify(triggerListener, never()).event(any(), any(), any());
  }

  @Test
  public void testTriggerWorkflowInstanceFailingStorage() throws Exception {
    Storage failingStorage = mock(Storage.class);
    when(failingStorage.workflow(any(WorkflowId.class))).thenThrow(new IOException("Error"));

    ServiceHelper serviceHelper = getServiceHelper(stateManager, failingStorage);
    serviceHelper.start();

    WorkflowInstance toTrigger = WorkflowInstance.create(HOURLY_WORKFLOW.id(), "2016-12-31T23");
    ByteString eventPayload = ByteString.of(OBJECT_MAPPER.writeValueAsBytes(toTrigger));
    CompletionStage<Response<ByteString>> post =
        serviceHelper.request("POST", BASE + "/trigger", eventPayload);
    Response<ByteString> response = post.toCompletableFuture().get();

    assertThat(response.status(),
               is(Status.INTERNAL_SERVER_ERROR
                      .withReasonPhrase("An error occurred while retrieving "
                                        + "workflow specifications")));
    verify(triggerListener, never()).event(any(), any(), any());
  }

  @Test
  public void testTriggerWorkflowInstanceUnsupportedSchedule() throws Exception {
    when(storage.workflow(DAILY_WORKFLOW.id())).thenReturn(Optional.of(MONTHLY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(MONTHLY_WORKFLOW.id(), "2014-12-01");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(), is(Status.OK));
    final Instant expectedInstant = Instant.parse("2014-12-01T00:00:00.000Z");
    verify(triggerListener).event(eq(MONTHLY_WORKFLOW), triggerCaptor.capture(), eq(expectedInstant));
  }

  @Test
  public void testTriggerWorkflowInstanceFuture() throws Exception {
    when(storage.workflow(HOURLY_WORKFLOW.id())).thenReturn(Optional.of(HOURLY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(HOURLY_WORKFLOW.id(), "2016-12-31T23");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(),
               is(Status.BAD_REQUEST.withReasonPhrase("Cannot trigger an instance of the future")));

    verify(triggerListener, never()).event(any(), any(), any());
  }

  @Test
  public void testTriggerWorkflowInstanceParseDayForHourly() throws Exception {
    when(storage.workflow(HOURLY_WORKFLOW.id())).thenReturn(Optional.of(HOURLY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(HOURLY_WORKFLOW.id(), "2015-12-31");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(), is(Status.OK));

    final Instant expectedInstant = Instant.parse("2015-12-31T00:00:00.000Z");
    verify(triggerListener).event(eq(HOURLY_WORKFLOW), triggerCaptor.capture(), eq(expectedInstant));
  }

  @Test
  public void testTriggerWorkflowInstanceParseFailure() throws Exception {
    when(storage.workflow(HOURLY_WORKFLOW.id())).thenReturn(Optional.of(HOURLY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(DAILY_WORKFLOW.id(), "2015");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(),
               is(Status.BAD_REQUEST.withReasonPhrase("Cannot parse time parameter 2015 - "
                                                      + "Text '2015' could not be parsed at index 4")));

    verify(triggerListener, never()).event(any(), any(), any());
  }

  @Test
  public void failTriggeringOnUnknownException() throws Exception {
    final Exception exception = new Exception("unknown exception!");
    failtriggeringOnException(DAILY_WORKFLOW, exception, Status.INTERNAL_SERVER_ERROR.withReasonPhrase(exception.getMessage()));
  }

  @Test
  public void failTriggeringOnIllegalArgumentException() throws Exception {
    final IllegalArgumentException exception = new IllegalArgumentException("illegal argument!");
    failtriggeringOnException(DAILY_WORKFLOW, exception, Status.CONFLICT.withReasonPhrase(exception.getMessage()));
  }

  @Test
  public void failTriggeringOnIllegalStateException() throws Exception {
    final IllegalStateException exception = new IllegalStateException("illegal state!");
    failtriggeringOnException(DAILY_WORKFLOW, exception, Status.CONFLICT.withReasonPhrase(exception.getMessage()));
  }

  @Test
  public void failTriggeringAlreadyActiveWorkflowInstance() throws Exception {
    final AlreadyInitializedException exception = new AlreadyInitializedException("already active!");
    failtriggeringOnException(DAILY_WORKFLOW, exception, Status.CONFLICT.withReasonPhrase(
        "This workflow instance is already triggered. Did you want to `retry` running it instead?"));
  }

  @Test
  public void testTriggerMissingImage() throws Exception {
    final WorkflowConfiguration workflowConfiguration =
        WorkflowConfigurationBuilder.from(FULL_DAILY_WORKFLOW.configuration())
            .dockerImage(Optional.empty()).build();
    final Workflow workflow = Workflow.create("styx", workflowConfiguration);
    when(storage.workflow(workflow.id())).thenReturn(Optional.of(workflow));
    WorkflowInstance toTrigger = WorkflowInstance.create(workflow.id(), "2015-12-31");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(),
               is(Status.BAD_REQUEST.withReasonPhrase("Workflow is missing docker image")));
    assertThat(triggeredWorkflow, isEmpty());
    assertThat(triggeredInstant, isEmpty());
  }

  @Test
  public void testTriggerInvalidWorkflowConfiguration() throws Exception {
    when(workflowValidator.validateWorkflow(DAILY_WORKFLOW)).thenReturn(ImmutableList.of("bad", "f00d"));

    when(storage.workflow(DAILY_WORKFLOW.id())).thenReturn(Optional.of(DAILY_WORKFLOW));
    WorkflowInstance toTrigger = WorkflowInstance.create(DAILY_WORKFLOW.id(), "2015-12-31");

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(),
               is(Status.BAD_REQUEST.withReasonPhrase("Invalid workflow configuration: bad, f00d")));
    assertThat(triggeredWorkflow, isEmpty());
    assertThat(triggeredInstant, isEmpty());
  }

  void failtriggeringOnException(Workflow wf, Exception e, StatusType preferredStatusType) throws Exception {
    when(storage.workflow(wf.id())).thenReturn(Optional.of(wf));
    WorkflowInstance toTrigger = WorkflowInstance.create(wf.id(), "2015-12-31");

    when(triggerListener.event(any(), any(), any())).thenReturn(
        CompletableFutures.exceptionallyCompletedFuture(e));

    Response<ByteString> response = requestAndWaitTriggerWorkflowInstance(toTrigger);

    assertThat(response.status(), is(preferredStatusType));
  }
}
