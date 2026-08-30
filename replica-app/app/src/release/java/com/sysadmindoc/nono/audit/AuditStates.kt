package com.sysadmindoc.nono.audit

import android.content.Intent
import com.sysadmindoc.nono.model.UiState

/**
 * Release twin of the debug audit-state harness.
 *
 * Returning null means the QA override is inert, and no intent extra is read, so a shipping
 * build contains none of the capture-reproduction machinery.
 */

fun readAuditState(intent: Intent): String = ""

fun auditStateFor(base: UiState, id: String): UiState? = null
