@file:Suppress("unused")

package io.github.proify.lyricon.xposed.systemui.util

import com.highcapable.yukihookapi.hook.log.YLog
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 状态栏禁用指令 Hook 引擎
 * 支持外部动态添加/移除监听器，实现业务与 Hook 的彻底解耦
 */
object StatusBarDisableHooker {

    private const val TAG = "StatusBarDisableHooker"

    // 状态标志位定义
    private const val FLAG_DISABLE_SYSTEM_INFO = 0x00800000
    private const val TARGET_CLASS =
        "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment"

    private val listeners = CopyOnWriteArrayList<OnStatusBarDisableListener>()

    /**
     * 外部注册监听器
     */
    fun addListener(listener: OnStatusBarDisableListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    /**
     * 外部移除监听器
     */
    fun removeListener(listener: OnStatusBarDisableListener) {
        listeners.remove(listener)
    }

    fun inject(appClassLoader: ClassLoader) {
        try {
            val targetClass = XposedHelpers.findClass(TARGET_CLASS, appClassLoader)
            val hook = object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val args = param.args ?: return
                    val state1 = args.filterIsInstance<Int>().getOrNull(1) ?: return
                    val animate = args.filterIsInstance<Boolean>().lastOrNull() ?: false

                    val shouldHide = (state1 and FLAG_DISABLE_SYSTEM_INFO != 0)

                    listeners.forEach {
                        try {
                            it.onDisableStateChanged(shouldHide, animate)
                        } catch (e: Exception) {
                            YLog.error("$TAG -> 鍒嗗彂鐩戝惉澶辫触: ${e.message}")
                        }
                    }
                }
            }

            val methods = targetClass.declaredMethods
                .filter { method ->
                    method.name == "disable" &&
                        method.parameterTypes.any { type -> type == Int::class.javaPrimitiveType }
                }

            if (methods.isEmpty()) {
                YLog.error("$TAG -> No disable methods found in $TARGET_CLASS")
            } else {
                methods.forEach { method ->
                    try {
                        XposedBridge.hookMethod(method, hook)
                        YLog.info("$TAG -> Hooked: ${method.name}(${method.parameterTypes.joinToString()})")
                    } catch (e: Throwable) {
                        YLog.error("$TAG -> Hook method failed: ${e.message}")
                    }
                }
            }
            return
            XposedHelpers.findAndHookMethod(
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
                appClassLoader,
                "disable",
                Int::class.javaPrimitiveType, // displayId
                Int::class.javaPrimitiveType, // state1
                Int::class.javaPrimitiveType, // state2
                Boolean::class.javaPrimitiveType, // animate
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state1 = param.args[1] as Int
                        val animate = param.args[3] as Boolean

                        val shouldHide = (state1 and FLAG_DISABLE_SYSTEM_INFO != 0)

                        listeners.forEach {
                            try {
                                it.onDisableStateChanged(shouldHide, animate)
                            } catch (e: Exception) {
                                YLog.error("$TAG -> 分发监听失败: ${e.message}")
                            }
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            YLog.error("$TAG -> Hook 注入失败: ${e.message}")
        }
    }

    interface OnStatusBarDisableListener {
        fun onDisableStateChanged(shouldHide: Boolean, animate: Boolean)
    }
}
