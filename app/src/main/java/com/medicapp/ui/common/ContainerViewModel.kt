package com.medicapp.ui.common

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.di.AppContainer
import com.medicapp.ui.LocalAppContainer

/** Construit un ViewModel à partir du conteneur d'injection applicative. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
