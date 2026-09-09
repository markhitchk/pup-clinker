import groovy.json.JsonSlurper
import java.io.File

// Dynamic roster extension layer. V1/V2 remain compatibility baselines; every
// manifest-backed test or v3+ folder is discovered without editing Kotlin lists.

data class DynamicAssetSpec(
    val styleId: String,
    val assetId: String,
    val folder: String,
    val fileName: String,
    val name: String,
    val emoji: String,
    val description: String,
    val free: Boolean,
    val redeemOnly: Boolean,
    val groupId: String,
    val groupTitle: String,
    val groupOrder: Int
)

val dynamicRepoAssets = rootProject.file("../assets")
val dynamicProtectedAssets = layout.buildDirectory.dir("generated/protected-puppies/assets/canonical-puppies")
val dynamicGeneratedSource = layout.buildDirectory.dir("generated/protected-puppies/source")
val dynamicMaxBytes = 8L * 1024L * 1024L
val dynamicPngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
val dynamicFolderRegex = Regex("v[3-9][0-9]*")
val dynamicIdRegex = Regex("[a-z0-9_]{1,64}")
val dynamicFileRegex = Regex("[A-Za-z0-9._-]{1,128}\\.png")

fun dynamicFolders(): List<File> = dynamicRepoAssets.listFiles()
    ?.filter { folder ->
        folder.isDirectory &&
            (folder.name == "test" || dynamicFolderRegex.matches(folder.name)) &&
            folder.resolve("manifest.json").isFile
    }
    ?.sortedWith(compareBy<File> {
        if (it.name == "test") Int.MAX_VALUE else it.name.removePrefix("v").toIntOrNull() ?: Int.MAX_VALUE - 1
    }.thenBy { it.name })
    ?: emptyList()

fun parseDynamicFolder(folder: File): List<DynamicAssetSpec> {
    val parsed = JsonSlurper().parse(folder.resolve("manifest.json")) as? Map<*, *>
        ?: error("Dynamic puppy manifest must be a JSON object: ${folder.path}")
    val puppies = parsed["puppies"] as? List<*>
        ?: error("Dynamic puppy manifest is missing puppies[]: ${folder.path}")
    val declaredCount = (parsed["count"] as? Number)?.toInt()
    if (declaredCount != null) {
        require(declaredCount == puppies.size) {
            "Dynamic puppy manifest count mismatch in ${folder.path}: declared $declaredCount, found ${puppies.size}"
        }
    }
    require(puppies.size <= 250) { "Dynamic puppy folder ${folder.name} contains too many puppies" }

    val roster = parsed["roster"]?.toString()?.trim().orEmpty()
    val ownership = parsed["ownership"]?.toString()?.trim()?.lowercase().orEmpty()
    val groupId = folder.name
    val groupTitle = when {
        folder.name == "test" -> "Test Puppies"
        roster.isNotBlank() -> "${roster.uppercase()} Puppies"
        else -> "${folder.name.uppercase()} Puppies"
    }
    val groupOrder = if (folder.name == "test") Int.MAX_VALUE else folder.name.removePrefix("v").toIntOrNull() ?: Int.MAX_VALUE - 1
    val seen = HashSet<String>()

    return puppies.mapIndexed { index, raw ->
        val item = raw as? Map<*, *>
            ?: error("Invalid puppy entry $index in ${folder.path}")
        val assetId = item["asset_id"]?.toString()?.trim().orEmpty()
        require(dynamicIdRegex.matches(assetId)) { "Invalid asset_id '$assetId' in ${folder.path}" }
        val requiredPrefix = if (folder.name == "test") "test_" else "${folder.name}_"
        require(assetId.startsWith(requiredPrefix)) {
            "asset_id '$assetId' in ${folder.name} must start with '$requiredPrefix'"
        }
        require(seen.add(assetId)) { "Duplicate asset_id '$assetId' in ${folder.path}" }

        val fileName = item["file"]?.toString()?.trim().orEmpty()
        require(dynamicFileRegex.matches(fileName)) { "Invalid PNG filename '$fileName' in ${folder.path}" }
        val source = folder.resolve(fileName)
        require(source.isFile) { "Missing dynamic puppy PNG: ${source.path}" }
        require(source.length() in 1..dynamicMaxBytes) { "Invalid dynamic puppy PNG size: ${source.path}" }
        val header = source.inputStream().use { input -> ByteArray(8).also { bytes -> require(input.read(bytes) == 8) } }
        require(header.contentEquals(dynamicPngSignature)) { "Dynamic puppy asset is not a PNG: ${source.path}" }

        val free = (item["free"] as? Boolean) ?: (ownership == "free")
        val redeemOnly = if (free) false else ((item["redeem_only"] as? Boolean) ?: true)
        val name = item["name"]?.toString()?.trim().orEmpty().ifBlank {
            assetId.removePrefix(requiredPrefix).split('_').joinToString(" ") { part ->
                part.replaceFirstChar { ch -> ch.uppercase() }
            }
        }
        require(name.length in 1..80) { "Invalid puppy name for '$assetId'" }
        val emoji = item["emoji"]?.toString()?.trim().orEmpty().ifBlank { if (folder.name == "test") "🧪" else "🐾" }
        val description = item["description"]?.toString()?.trim().orEmpty().ifBlank {
            if (folder.name == "test") "Free test roster puppy." else "${groupTitle.removeSuffix(" Puppies")} roster puppy."
        }
        require(description.length <= 240) { "Description too long for '$assetId'" }

        DynamicAssetSpec(
            styleId = assetId,
            assetId = assetId,
            folder = folder.name,
            fileName = fileName,
            name = name,
            emoji = emoji,
            description = description,
            free = free,
            redeemOnly = redeemOnly,
            groupId = groupId,
            groupTitle = groupTitle,
            groupOrder = groupOrder
        )
    }
}

fun dynamicSpecs(): List<DynamicAssetSpec> = dynamicFolders().flatMap(::parseDynamicFolder)

fun kotlinQuote(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '$' -> append("\\$")
            else -> append(ch)
        }
    }
    append('"')
}

tasks.named("prepareProtectedPuppyAssets").configure {
    inputs.dir(dynamicRepoAssets)
    doLast {
        val outputRoot = dynamicProtectedAssets.get().asFile
        dynamicFolders().forEach { folder ->
            val target = outputRoot.resolve(folder.name).apply { mkdirs() }
            parseDynamicFolder(folder).forEach { spec ->
                folder.resolve(spec.fileName).copyTo(target.resolve(spec.fileName), overwrite = true)
            }
        }
    }
}

tasks.named("generateProtectedPuppySources").configure {
    inputs.dir(dynamicRepoAssets)
    doLast {
        val specs = dynamicSpecs()
        val target = dynamicGeneratedSource.get().asFile
            .resolve("com/harleytg/puppyclicker/GeneratedDynamicPuppyRoster.kt")
        target.parentFile.mkdirs()

        val body = buildString {
            appendLine("package com.harleytg.puppyclicker")
            appendLine()
            appendLine("// Generated from assets/test and assets/v3+ manifests. Do not edit by hand.")
            appendLine("val EXTRA_PUPPY_DESCRIPTORS: List<PuppyRosterAsset> = listOf(")
            specs.forEach { spec ->
                append("    PuppyRosterAsset(")
                append("style = PuppyStyle(")
                append(kotlinQuote(spec.styleId)).append(", ")
                append(kotlinQuote(spec.name)).append(", ")
                append(kotlinQuote(spec.emoji)).append(", ")
                append(kotlinQuote(spec.description)).append(", redeemOnly = ").append(spec.redeemOnly)
                append("), assetId = ").append(kotlinQuote(spec.assetId))
                append(", folder = ").append(kotlinQuote(spec.folder))
                append(", fileName = ").append(kotlinQuote(spec.fileName))
                append(", free = ").append(spec.free)
                append(", groupId = ").append(kotlinQuote(spec.groupId))
                append(", groupTitle = ").append(kotlinQuote(spec.groupTitle))
                append(", groupOrder = ").append(spec.groupOrder)
                appendLine("),")
            }
            appendLine(")")
            appendLine("val EXTRA_PUPPY_STYLES: List<PuppyStyle> = EXTRA_PUPPY_DESCRIPTORS.map { it.style }")
            appendLine("val EXTRA_PUPPY_IDS: Set<String> = EXTRA_PUPPY_STYLES.mapTo(linkedSetOf()) { it.id }")
            appendLine("val EXTRA_FREE_PUPPY_IDS: Set<String> = EXTRA_PUPPY_DESCRIPTORS.filter { it.free }.mapTo(linkedSetOf()) { it.style.id }")
        }
        target.writeText(body)
    }
}
