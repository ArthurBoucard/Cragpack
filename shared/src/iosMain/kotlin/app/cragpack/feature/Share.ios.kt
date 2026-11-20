package app.cragpack.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareText(): (text: String) -> Unit = remember {
    { text ->
        // ponytail: iPhone only; an iPad would need the sheet anchored to the Share button.
        UIApplication.sharedApplication.keyWindow?.rootViewController
            ?.presentViewController(UIActivityViewController(listOf(text), null), animated = true, completion = null)
    }
}
