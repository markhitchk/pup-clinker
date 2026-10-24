package com.harleytg.puppypointer

import android.graphics.BitmapFactory
import android.view.MotionEvent
import android.view.PointerIcon
import com.facebook.react.ReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManager
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.views.view.ReactViewGroup
import com.facebook.react.views.view.ReactViewManager

// Match the Kotlin Compose root's pointerHoverIcon. Native OS cursor only:
// no touch-following overlay, no interception of scrolling/clicks/accessibility.
class PuppyPointerView(context: ThemedReactContext) : ReactViewGroup(context) {
    var darkTheme = true
    var puppyEnabled = true
    private val icons = mutableMapOf<Boolean, PointerIcon>()

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        if (!puppyEnabled) return super.onResolvePointerIcon(event, pointerIndex)
        return icons.getOrPut(darkTheme) {
            val theme = if (darkTheme) "dark" else "light"
            val resourceId = resources.getIdentifier("puppy_pointer_${theme}_default", "drawable", context.packageName)
            val options = BitmapFactory.Options().apply { inScaled = false }
            val bitmap = checkNotNull(BitmapFactory.decodeResource(resources, resourceId, options))
            check(bitmap.width == 64 && bitmap.height == 64)
            PointerIcon.create(bitmap, 16f, 16f)
        }
    }
}

class PuppyPointerManager : ReactViewManager() {
    override fun getName() = "PuppyPointerView"
    override fun createViewInstance(context: ThemedReactContext): ReactViewGroup = PuppyPointerView(context)

    @ReactProp(name = "darkTheme", defaultBoolean = true)
    fun setDarkTheme(view: ReactViewGroup, value: Boolean) { (view as PuppyPointerView).darkTheme = value }

    @ReactProp(name = "puppyEnabled", defaultBoolean = true)
    fun setPuppyEnabled(view: ReactViewGroup, value: Boolean) { (view as PuppyPointerView).puppyEnabled = value }
}

class PuppyPointerPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext): List<NativeModule> = emptyList()
    override fun createViewManagers(context: ReactApplicationContext): List<ViewManager<*, *>> = listOf(PuppyPointerManager())
}
