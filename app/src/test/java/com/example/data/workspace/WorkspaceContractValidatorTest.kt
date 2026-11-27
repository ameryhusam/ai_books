package com.example.data.workspace

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkspaceContractValidatorTest {

    @Test
    fun unresolvedProposalIsNotPromotedFromLessonPath() {
        val unit = Json.parseToJsonElement(
            """{"partKey":"unknown","proposedPartKey":"P01","assignmentStatus":"need_review","lessons":[{"sliceFile":{"workspaceUri":"workspace/G04/SCI/P01/units/unit_01/lesson_01.pdf"}}]}"""
        ).jsonObject

        assertEquals("", WorkspaceContractValidator.resolveUnitPartKey(unit))
    }

    @Test
    fun explicitPartAssignmentIsPreservedEvenWhenReviewIsRequired() {
        val unit = Json.parseToJsonElement(
            """{"partKey":"P02","proposedPartKey":"P01","assignmentStatus":"need_review"}"""
        ).jsonObject

        assertEquals("P02", WorkspaceContractValidator.resolveUnitPartKey(unit))
    }

    @Test
    fun lessonPathCanProvideFallbackOnlyWhenAssignmentIsNotProvisional() {
        val unit = Json.parseToJsonElement(
            """{"partKey":"unknown","assignmentStatus":"assigned","lessons":[{"sliceFile":{"workspaceUri":"workspace/G04/SCI/P01/units/unit_01/lesson_01.pdf"}}]}"""
        ).jsonObject

        assertEquals("P01", WorkspaceContractValidator.resolveUnitPartKey(unit))
    }
}
