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

package com.spotify.styx.util;

import static com.spotify.styx.util.MDCUtil.withMDC;
import static java.util.Collections.emptyMap;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;
import org.slf4j.MDC.MDCCloseable;

public class MDCUtilTest {

  private ExecutorService executor;

  @Before
  public void setUp() throws Exception {
    MDC.clear();
    executor = Executors.newSingleThreadExecutor();
  }

  @After
  public void tearDown() throws Exception {
    executor.shutdownNow();
    MDC.clear();
  }

  @Test
  public void withMDCRunnable() throws ExecutionException, InterruptedException {
    MDC.put("foo", "bar");
    final CompletableFuture<String> value = new CompletableFuture<>();
    executor.submit(withMDC(() -> value.complete(MDC.get("foo"))));
    assertThat(value.get(), is("bar"));
  }

  @Test
  public void withMDCCallable() throws ExecutionException, InterruptedException {
    MDC.put("foo", "bar");
    final String value = executor.submit(withMDC(() -> MDC.get("foo"))).get();
    assertThat(value, is("bar"));
  }

  @Test
  public void withMDCCommonPool() throws ExecutionException, InterruptedException {
    MDC.put("foo", "bar");
    final CompletableFuture<String> value1 = new CompletableFuture<>();
    final CompletableFuture<String> value2 = new CompletableFuture<>();
    CompletableFuture.runAsync(
        () -> value1.complete(MDC.get("foo")),
        withMDC())
        // Later stages should also have the MDC applied
        .thenRun(() -> value2.complete(MDC.get("foo")))
        .get();
    assertThat(value1.getNow(""), is("bar"));
    assertThat(value2.getNow(""), is("bar"));

    // MDC should not leak
    final Map<String, String> mdc = ForkJoinPool.commonPool()
        .submit(MDC::getCopyOfContextMap)
        .get();
    assertThat(mdc, is(anyOf(nullValue(), is(emptyMap()))));
  }

  @Test
  public void withMDCExecutor() throws ExecutionException, InterruptedException {
    MDC.put("foo", "bar");
    final CompletableFuture<String> value1 = new CompletableFuture<>();
    final CompletableFuture<String> value2 = new CompletableFuture<>();
    CompletableFuture.runAsync(
        () -> value1.complete(MDC.get("foo")),
        withMDC(executor))
        // Later stages should also have the MDC applied
        .thenRun(() -> value2.complete(MDC.get("foo")))
        .get();
    assertThat(value1.getNow(""), is("bar"));
    assertThat(value2.getNow(""), is("bar"));

    // MDC should not leak
    final Map<String, String> mdc = executor
        .submit(MDC::getCopyOfContextMap)
        .get();
    assertThat(mdc, is(anyOf(nullValue(), is(emptyMap()))));
  }

  @Test
  public void safePutCloseable() {
    try (MDCCloseable mdc = MDCUtil.safePutCloseable("foo", "bar")) {
      assertThat(MDC.get("foo"), is("bar"));
    }
    assertThat(MDC.get("foo"), is(nullValue()));
  }
}