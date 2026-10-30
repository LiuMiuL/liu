package com.miu.videograb.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class JobStatus(val label: String) {
    QUEUED("대기 중"), RUNNING("다운로드 중"), SAVING("갤러리에 저장 중"), DONE("완료"), FAILED("실패"), CANCELLED("취소됨");

    val isActive: Boolean get() = this == QUEUED || this == RUNNING || this == SAVING
}

/** 다운로드 작업 1건. 링크 하나에 한 행. */
@Entity(tableName = "jobs", indices = [Index("status"), Index("urlKey")])
data class DownloadJob(
    @PrimaryKey val id: String,
    val url: String,
    /** 같은 링크 중복 판단 키(UrlExtractor.normalize). */
    val urlKey: String,
    val preset: String,
    val status: JobStatus,
    /** 제목. 다운로드가 시작돼 파일 이름을 알기 전에는 null. */
    val title: String? = null,
    /** 0~100. 모르면 null. */
    val progress: Float? = null,
    val etaSeconds: Long? = null,
    /** 저장된 파일 content:// URI(여러 개면 줄바꿈 구분). */
    val outputUris: String? = null,
    val outputCount: Int = 0,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
    val errorDetail: String? = null,
    val suggestEngineUpdate: Boolean = false,
    /** 사이트 변경 오류 뒤 엔진을 업데이트하고 자동으로 한 번 다시 시도했는지. */
    val autoRetried: Boolean = false,
    /** 앱이 강제 종료돼 이어 받기로 다시 시작한 횟수. */
    val restarts: Int = 0,
    val createdAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
) {
    val firstOutputUri: String? get() = outputUris?.lineSequence()?.firstOrNull()?.ifBlank { null }
}
