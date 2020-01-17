/*
 *
 *  * Copyright 2020 Google LLC
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     https://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.google.android.gnd.persistence.remote.firestore.converters;

import static com.google.android.gnd.persistence.remote.firestore.schema.FeatureDocumentReference.CREATED;
import static com.google.android.gnd.persistence.remote.firestore.schema.FeatureDocumentReference.LAST_MODIFIED;
import static com.google.android.gnd.persistence.remote.firestore.schema.FeatureDocumentReference.LAYER_ID;
import static com.google.android.gnd.persistence.remote.firestore.schema.FeatureDocumentReference.LOCATION;

import com.google.android.gnd.model.Project;
import com.google.android.gnd.model.feature.Feature;
import com.google.android.gnd.model.layer.Layer;
import com.google.android.gnd.persistence.remote.firestore.base.FirestoreData;
import com.google.firebase.firestore.DocumentSnapshot;

public class FeatureDataConverter {

  public static Feature toFeature(DocumentSnapshot doc, Project project) {
    FirestoreData data = new FirestoreData(doc.getData());
    String layerId = data.getRequired(LAYER_ID);
    Layer layer =
        project
            .getLayer(layerId)
            .orElseThrow(() -> new IllegalStateException("Unknown layer " + layerId));
    return Feature.newBuilder()
        .setId(doc.getId())
        .setProject(project)
        .setLayer(layer)
        .setPoint(GeoPointConverter.toPoint(data.getRequired(LOCATION)))
        .setCreated(data.getRequired(CREATED).toAuditInfo())
        .setLastModified(data.getRequired(LAST_MODIFIED).toAuditInfo())
        .build();
  }
}
