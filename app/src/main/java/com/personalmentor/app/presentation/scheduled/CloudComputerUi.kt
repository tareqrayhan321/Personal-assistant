package com.personalmentor.app.presentation.scheduled

import com.personalmentor.app.domain.model.CloudComputer

/** Everything the editor needs to pick, create, edit, delete and test cloud computers. */
internal class CloudComputerUi(
    val computers: List<CloudComputer>,
    val onSave: (CloudComputer, (Long) -> Unit) -> Unit,
    val onDelete: (CloudComputer) -> Unit,
    val onTest: (CloudComputer, (String) -> Unit) -> Unit,
)
