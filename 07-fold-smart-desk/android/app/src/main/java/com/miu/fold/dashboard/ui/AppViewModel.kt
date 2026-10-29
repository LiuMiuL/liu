package com.miu.fold.dashboard.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.miu.fold.dashboard.AppContainer
import com.miu.fold.dashboard.FoldDeskApp

/** 단일 Activity의 ViewModelStore를 공유하므로 같은 타입은 화면 간에 같은 인스턴스다. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as FoldDeskApp
    return viewModel(factory = viewModelFactory {
        initializer { create(app.container, createSavedStateHandle()) }
    })
}
