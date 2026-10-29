package com.miu.fold.dashboard.core

import java.util.UUID

object Ids {
    fun newId(): String = UUID.randomUUID().toString()
}
