/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.ground.repository;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.ground.model.AuditInfo;
import com.google.android.ground.model.Project;
import com.google.android.ground.model.User;
import com.google.android.ground.model.feature.Feature;
import com.google.android.ground.model.feature.Point;
import com.google.android.ground.model.layer.Layer;
import org.junit.Before;
import org.junit.Test;

public class InMemoryCacheTest {

  private InMemoryCache inMemoryCache;

  private static final User FAKE_USER =
      User.builder().setId("id").setDisplayName("name").setEmail("email").build();

  private static final Feature FAKE_FEATURE =
      Feature.newBuilder()
          .setId("foo feature id")
          .setProject(Project.newBuilder().build())
          .setLayer(Layer.newBuilder().build())
          .setPoint(Point.newBuilder().setLatitude(0.0).setLongitude(0.0).build())
          .setCreated(AuditInfo.now(FAKE_USER))
          .setLastModified(AuditInfo.now(FAKE_USER))
          .build();

  @Before
  public void setUp() {
    inMemoryCache = new InMemoryCache();
  }

  @Test
  public void getFeatures_EmptyIfNoneAdded() {
    assertThat(inMemoryCache.getFeatures()).isEmpty();
  }

  @Test
  public void putFeature() {
    inMemoryCache.putFeature(FAKE_FEATURE);

    assertThat(inMemoryCache.getFeatures()).containsExactly(FAKE_FEATURE);
  }

  @Test
  public void removeFeature() {
    inMemoryCache.putFeature(FAKE_FEATURE);
    inMemoryCache.removeFeature(FAKE_FEATURE.getId());

    assertThat(inMemoryCache.getFeatures()).isEmpty();
  }

  @Test
  public void clear_ClearsFeatures() {
    inMemoryCache.putFeature(FAKE_FEATURE);
    inMemoryCache.clear();

    assertThat(inMemoryCache.getFeatures()).isEmpty();
  }
}