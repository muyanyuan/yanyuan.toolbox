package com.yanyuan.toolbox.ui

sealed interface WorkspaceRunPhase {
    data object Idle : WorkspaceRunPhase
    data object Thinking : WorkspaceRunPhase
    data object RunningTool : WorkspaceRunPhase
    data object WaitingApproval : WorkspaceRunPhase
    data object ReviewingChanges : WorkspaceRunPhase
    data object ApplyingChanges : WorkspaceRunPhase
    data object Completed : WorkspaceRunPhase
    data class Failed(val message: String) : WorkspaceRunPhase
}

enum class ToolCallState { PREPARING, RUNNING, OK, ERROR, STOPPED, DENIED }
enum class ToolVariant { READ, LIST, WRITE, EDIT, OTHER }

data class ToolCallUi(
    val id: String,
    val parentId: String? = null,
    val action: String,
    val variant: ToolVariant,
    val path: String? = null,
    val inputSummary: String = "",
    val output: String = "",
    val error: String? = null,
    val state: ToolCallState = ToolCallState.PREPARING,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val expanded: Boolean = false
)

enum class FileChangeState { PENDING, APPROVED, REJECTED, APPLIED, FAILED }

data class FileChangeUi(
    val id: String,
    val path: String,
    val existed: Boolean,
    val oldText: String?,
    val newText: String,
    val addedLines: Int,
    val removedLines: Int,
    val byteSize: Int,
    val state: FileChangeState = FileChangeState.PENDING,
    val expanded: Boolean = false
)
