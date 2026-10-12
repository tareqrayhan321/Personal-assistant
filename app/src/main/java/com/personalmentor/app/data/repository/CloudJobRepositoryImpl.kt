package com.personalmentor.app.data.repository

import com.personalmentor.app.domain.cloud.CloudAgentApi
import com.personalmentor.app.domain.cloud.CloudJobService
import com.personalmentor.app.domain.cloud.SubmissionKeys
import com.personalmentor.app.domain.repository.CloudJobRepository
import com.personalmentor.app.domain.repository.CloudSettingsRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Wires the tested [CloudJobService] to the real API and settings. Cloud jobs are not stored in Room. */
@Singleton
class CloudJobRepositoryImpl @Inject constructor(
    api: CloudAgentApi,
    settings: CloudSettingsRepository,
) : CloudJobRepository by CloudJobService(
    api = api,
    settings = { settings.current() },
    keys = SubmissionKeys { "app-" + UUID.randomUUID().toString().replace("-", "") },
)
