/*
 * -\-\-
 * Spotify Styx Scheduler Service
 * --
 * Copyright (C) 2017 Spotify AB
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

import static com.google.common.collect.Sets.newHashSet;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.google.common.base.Strings;
import org.junit.Test;

public class DockerImageValidatorTest {

  DockerImageValidator validator = new DockerImageValidator();

  @Test
  public void testValidImagePasses() {
    assertThat(validator.validateImageReference("repo"), is(empty()));
    assertThat(validator.validateImageReference("namespace/repo"), is(empty()));
    assertThat(validator.validateImageReference("namespace/repo:tag"), is(empty()));
    assertThat(validator.validateImageReference("namespace/repo:1.2"), is(empty()));
    assertThat(validator.validateImageReference("reg.istry:4711/repo"), is(empty()));
    assertThat(validator.validateImageReference("reg.istry.:4711/repo"), is(empty()));
    assertThat(validator.validateImageReference("reg.istry:4711/namespace/repo"),
        is(empty()));
    assertThat(validator.validateImageReference("reg.istry.:4711/namespace/repo"),
        is(empty()));
    assertThat(validator.validateImageReference("1.2.3.4:4711/namespace/repo"), is(empty()));
    assertThat(validator.validateImageReference("registry.test.net:80/fooo/bar"),
        is(empty()));
    assertThat(validator.validateImageReference("registry.test.net.:80/fooo/bar"),
        is(empty()));
    assertThat(validator.validateImageReference(
        "namespace/foo@sha256:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"
    ), is(empty()));
    assertThat(validator.validateImageReference(
        "foo.net/bar@sha256:2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae"
    ), is(empty()));
    assertThat(validator.validateImageReference(
        "foo@tarsum.v1+sha256:6c3c624b58dbbcd3c0dd82b4c53f04194d1247c6eebdaab7c610cf7d66709b3b"
    ), is(empty()));
  }

  @Test
  public void testInvalidImagesFail() throws Exception {
    assertEquals(newHashSet("Tag cannot be empty"),
        validator.validateImageReference("repo:"));

    assertEquals(newHashSet("Digest cannot be empty"),
        validator.validateImageReference("foo@"));

    assertEquals(newHashSet("Illegal digest: \":123\""),
        validator.validateImageReference("foo@:123"));

    assertEquals(newHashSet("Illegal digest: \"sha256:\""),
        validator.validateImageReference("foo@sha256:"));

    assertFalse(validator.validateImageReference("repo:/").isEmpty());

    assertEquals(newHashSet("Invalid domain name: \"1.2.3.4.\""),
        validator.validateImageReference("1.2.3.4.:4711/namespace/repo"));

    assertEquals(newHashSet("Invalid domain name: \" reg.istry\""),
        validator.validateImageReference(" reg.istry:4711/repo"));

    assertEquals(newHashSet("Invalid domain name: \"reg .istry\""),
        validator.validateImageReference("reg .istry:4711/repo"));

    assertEquals(newHashSet("Invalid domain name: \"reg.istry \""),
        validator.validateImageReference("reg.istry :4711/repo"));

    assertEquals(newHashSet("Invalid port in endpoint: \"reg.istry: 4711\""),
        validator.validateImageReference("reg.istry: 4711/repo"));

    assertEquals(newHashSet("Invalid port in endpoint: \"reg.istry:4711 \""),
        validator.validateImageReference("reg.istry:4711 /repo"));

    assertEquals(newHashSet("Invalid image name (reg.istry:4711/ repo), only ^([a-z0-9._-]+)$ is "
            + "allowed for each slash-separated name component "
            + "(failed on \" repo\")"),
        validator.validateImageReference("reg.istry:4711/ repo"));

    assertEquals(newHashSet("Invalid image name (reg.istry:4711/namespace /repo), only "
            + "^([a-z0-9._-]+)$ is allowed for each slash-separated name component "
            + "(failed on \"namespace \")"),
        validator.validateImageReference("reg.istry:4711/namespace /repo"));

    assertEquals(newHashSet("Invalid image name (reg.istry:4711/namespace/ repo), only "
            + "^([a-z0-9._-]+)$ is allowed for each slash-separated name component "
            + "(failed on \" repo\")"),
        validator.validateImageReference("reg.istry:4711/namespace/ repo"));

    assertEquals(newHashSet("Invalid image name (reg.istry:4711/namespace/repo ), only "
            + "^([a-z0-9._-]+)$ is allowed for each slash-separated name component "
            + "(failed on \"repo \")"),
        validator.validateImageReference("reg.istry:4711/namespace/repo "));

    assertEquals(newHashSet("Invalid domain name: \"foo-.ba|z\""),
        validator.validateImageReference("foo-.ba|z/namespace/baz"));

    assertEquals(newHashSet("Invalid domain name: \"reg..istry\""),
        validator.validateImageReference("reg..istry/namespace/baz"));

    assertEquals(newHashSet("Invalid domain name: \"reg..istry\""),
        validator.validateImageReference("reg..istry/namespace/baz"));

    assertEquals(newHashSet("Invalid port in endpoint: \"foo:345345345\""),
        validator.validateImageReference("foo:345345345/namespace/baz"));

    assertEquals(newHashSet("Invalid port in endpoint: \"foo:-17\""),
        validator.validateImageReference("foo:-17/namespace/baz"));

    final String foos = Strings.repeat("foo", 100);
    final String image = foos + "/bar";
    assertEquals(newHashSet("Invalid image name (" + image + "), repository name cannot be larger"
            + " than 255 characters"),
        validator.validateImageReference(image));
  }
}