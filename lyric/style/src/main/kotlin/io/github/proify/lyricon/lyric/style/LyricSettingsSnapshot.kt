/*
 * Copyright 2026 Proify, Tomakino
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.proify.lyricon.lyric.style

import kotlinx.serialization.Serializable

@Serializable
data class LyricSettingsSnapshot(
    val defaultPackageName: String,
    val baseStyle: BasicStyle,
    val packageStyles: Map<String, PackageStyle>,
    val enabledPackages: Set<String>,
    val translationConfigs: Map<String, TranslationConfig>
)
