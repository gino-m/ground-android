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

package com.google.android.gnd.persistence.local.room;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.android.gnd.model.form.Form;
import com.google.auto.value.AutoValue;
import com.google.auto.value.AutoValue.CopyAnnotations;

@AutoValue
@Entity(tableName = "form")
public abstract class FormEntity {

  @CopyAnnotations
  @NonNull
  @PrimaryKey
  @ColumnInfo(name = "id")
  public abstract String getId();

  @CopyAnnotations
  @Nullable
  @ColumnInfo(name = "title")
  public abstract String getTitle();

  @CopyAnnotations
  @NonNull
  @ColumnInfo(name = "layer_id")
  public abstract String getLayerId();

  public static FormEntity fromForm(String layerId, Form form) {
    return FormEntity.builder()
        .setId(form.getId())
        .setLayerId(layerId)
        .setTitle(form.getTitle())
        .build();
  }

  public static FormEntity create(String id, String title, String layerId) {
    return builder().setId(id).setTitle(title).setLayerId(layerId).build();
  }

  public static Builder builder() {
    return new AutoValue_FormEntity.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setId(String id);

    public abstract Builder setTitle(String title);

    public abstract Builder setLayerId(String layerId);

    public abstract FormEntity build();
  }
}
