/*
 * Copyright 2020 Google LLC
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

package com.google.android.ground.persistence.remote.firebase.schema

import com.google.android.ground.model.User
import com.google.android.ground.model.mutation.Mutation
import com.google.android.ground.model.mutation.SubmissionMutation
import com.google.android.ground.model.submission.*
import com.google.android.ground.persistence.remote.DataStoreException
import com.google.android.ground.persistence.remote.firebase.schema.AuditInfoConverter.fromMutationAndUser
import com.google.firebase.firestore.FieldValue
import kotlinx.collections.immutable.toPersistentMap
import timber.log.Timber

/** Converts between Firestore maps used to merge updates and [SubmissionMutation] instances. */
internal object SubmissionMutationConverter {

  const val LOI_ID = "loiId"
  private const val JOB_ID = "jobId"
  private const val DATA = "data"
  private const val CREATED = "created"
  private const val LAST_MODIFIED = "lastModified"

  @Throws(DataStoreException::class)
  fun toMap(mutation: SubmissionMutation, user: User): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val auditInfo = fromMutationAndUser(mutation, user)
    when (mutation.type) {
      Mutation.Type.CREATE -> {
        map[CREATED] = auditInfo
        map[LAST_MODIFIED] = auditInfo
      }
      Mutation.Type.UPDATE -> {
        map[LAST_MODIFIED] = auditInfo
      }
      Mutation.Type.DELETE,
      Mutation.Type.UNKNOWN -> {
        throw DataStoreException("Unsupported mutation type: ${mutation.type}")
      }
    }
    map[LOI_ID] = mutation.locationOfInterestId
    map[JOB_ID] = mutation.job.id
    map[DATA] = toMap(mutation.deltas)
    return map.toPersistentMap()
  }

  private fun toMap(deltas: List<ValueDelta>): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    for (delta in deltas) {
      map[delta.taskId] = toObject(delta.newValue) ?: FieldValue.delete()
    }
    return map.toPersistentMap()
  }

  private fun toObject(value: Value?): Any? =
    when (value) {
      is TextResponse -> {
        value.text
      }
      is MultipleChoiceResponse -> {
        value.selectedOptionIds
      }
      is NumberResponse -> {
        value.value
      }
      is TimeResponse -> {
        value.time
      }
      is DateResponse -> {
        value.date
      }
      is GeometryTaskResponse -> {
        GeometryConverter.toFirestoreMap(value.geometry).getOrThrow()
      }
      is CaptureLocationResult -> {
        CaptureLocationResultConverter.toFirestoreMap(value).getOrThrow()
      }
      else -> {
        Timber.e("Unknown value type: %s", value?.javaClass?.name)
        null
      }
    }
}
