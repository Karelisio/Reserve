package com.karelisio.reserve.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.karelisio.reserve.AppContainer
import com.karelisio.reserve.container

/** Creates a ViewModel with access to the [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    owner: ViewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current),
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(
        viewModelStoreOwner = owner,
        key = key,
        factory = viewModelFactory { initializer { create(container) } },
    )
}
