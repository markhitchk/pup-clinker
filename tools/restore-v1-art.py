#!/usr/bin/env python3
"""Restore the 18 approved V1 illustrations without changing V2 or gameplay.

Run from the repository root:
    python tools/restore-v1-art.py /path/to/v1-art.zip

The ZIP must contain exactly one v1_<id>.webp per V1 character. This script
intentionally does not contain artwork or substitute generic puppy redraws.
"""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT = Path.cwd()
APP = ROOT / 'app'
GRADLE = APP / 'build.gradle.kts'
RENDERER = APP / 'src/main/java/com/harleytg/puppyclicker/ProtectedPuppyArt.kt'
IDS = ['classic', 'golden', 'poodle', 'spotty', 'midnight', 'cloud', 'aurora', 'cocoa', 'snowball', 'galaxy', 'neon_buddy', 'golden_night', 'halloween', 'santa', 'birthday', 'dev_pup', 'secret_snoot', 'classic_forever']


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{label}: expected one matching block, found {count}')
    return text.replace(old, new, 1)


def validate_archive(archive):
    expected = {f'v1_{item}.webp' for item in IDS}
    with zipfile.ZipFile(archive) as z:
        if set(z.namelist()) != expected:
            raise RuntimeError('The archive must contain exactly the 18 V1 WebP files.')
        assets = {}
        for name in sorted(expected):
            data = z.read(name)
            if len(data) < 30 or data[:4] != b'RIFF' or data[8:12] != b'WEBP' or data[12:16] != b'VP8X':
                raise RuntimeError(f'Invalid extended WebP: {name}')
            width = 1 + int.from_bytes(data[24:27], 'little')
            height = 1 + int.from_bytes(data[27:30], 'little')
            if (width, height) != (384, 384) or len(data) < 3000:
                raise RuntimeError(f'Invalid dimensions or incomplete artwork: {name}')
            assets[name] = data
    if len({hashlib.sha256(data).digest() for data in assets.values()}) != len(IDS):
        raise RuntimeError('Duplicate puppy artwork was found.')
    return assets


def patch_gradle(text):
    text = replace_once(text, 'val protectedPuppySourceDir = layout.projectDirectory.dir("src/main/res/drawable-anydpi")', 'val protectedPuppySourceDir = layout.projectDirectory.dir("src/main/res/drawable-anydpi")\nval protectedV1OriginalDir = layout.projectDirectory.dir("src/main/puppy-art/v1")', 'V1 source directory')
    text = replace_once(text, 'Encrypts all V1/V2 Android vector puppy artwork into generated APK assets.', 'Encrypts original V1 character illustrations and existing V2 vectors into generated APK assets.', 'task description')
    text = replace_once(text, 'inputs.files(v1ProtectedPuppyIds.map { protectedPuppySourceDir.file("v1_$it.xml") })', 'inputs.files(v1ProtectedPuppyIds.map { protectedV1OriginalDir.file("v1_$it.webp") })', 'V1 inputs')
    text = replace_once(text, 'val source = protectedPuppySourceDir.file("$assetId.xml").asFile\n            require(source.isFile) { "Missing protected V1 puppy vector: ${source.path}" }', 'val source = protectedV1OriginalDir.file("$assetId.webp").asFile\n            require(source.isFile) { "Missing original V1 puppy artwork: ${source.path}" }\n            val header = source.inputStream().use { it.readNBytes(12) }\n            require(header.size == 12 && String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WEBP") { "Invalid original V1 WebP: ${source.path}" }', 'V1 encryption input')
    return text


def patch_renderer(text):
    text = replace_once(text, 'import android.graphics.Color as AndroidColor', 'import android.graphics.BitmapFactory\nimport android.graphics.Color as AndroidColor', 'bitmap import')
    text = replace_once(text, 'import androidx.compose.foundation.Canvas', 'import androidx.compose.foundation.Canvas\nimport androidx.compose.foundation.Image', 'image import')
    text = replace_once(text, 'import androidx.compose.ui.graphics.ColorFilter', 'import androidx.compose.ui.graphics.ColorFilter\nimport androidx.compose.ui.graphics.ImageBitmap\nimport androidx.compose.ui.graphics.asImageBitmap\nimport androidx.compose.ui.layout.ContentScale', 'image imports')
    text = replace_once(text, ' * Every V1 puppy now has its own vector model. V1 no longer reuses one bitmap\n * with a tint/filter. V2 keeps its existing vector models. Both generations are\n * AES-256-GCM protected during the build and decrypted only when rendered.\n *\n * Decryption and vector parsing stay off the UI thread so opening the roster does\n * not freeze the app on slower Android devices.', ' * V1 uses each character\'s original illustration, never a generic redraw or tint.\n * V2 retains its existing Android VectorDrawable models. Both generations are\n * AES-256-GCM protected during the build and decrypted only when rendered.\n * Decoding and vector parsing stay off the UI thread on slower devices.', 'renderer documentation')
    old = '''    val vector = produceState<SecureVector?>(
        initialValue = ProtectedPuppyAssets.peek(style.id),
        key1 = style.id
    ) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                runCatching { ProtectedPuppyAssets.load(appContext, style.id) }.getOrNull()
            }
        }
    }.value'''
    new = '''    val isOriginalV1 = style.id in V1_PUPPY_IDS
    val originalBitmap = produceState<ImageBitmap?>(
        initialValue = if (isOriginalV1) ProtectedPuppyAssets.peekBitmap(style.id) else null,
        key1 = style.id
    ) {
        if (isOriginalV1 && value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { ProtectedPuppyAssets.loadBitmap(appContext, style.id) }.getOrNull()
            }
        }
    }.value
    val vector = produceState<SecureVector?>(
        initialValue = if (isOriginalV1) null else ProtectedPuppyAssets.peek(style.id),
        key1 = style.id
    ) {
        if (!isOriginalV1 && value == null) {
            value = withContext(Dispatchers.Default) {
                runCatching { ProtectedPuppyAssets.load(appContext, style.id) }.getOrNull()
            }
        }
    }.value'''
    text = replace_once(text, old, new, 'portrait loading')
    text = replace_once(text, '        if (vector != null) {\n            ProtectedVectorImage(', '''        if (originalBitmap != null) {
            Image(
                bitmap = originalBitmap,
                contentDescription = style.name,
                modifier = Modifier.size(size * 0.96f),
                contentScale = ContentScale.Fit
            )
        } else if (vector != null) {
            ProtectedVectorImage(''', 'portrait rendering')
    text = replace_once(text, 'private val cache = ConcurrentHashMap<String, SecureVector>()', 'private val cache = ConcurrentHashMap<String, SecureVector>()\n    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap>()', 'bitmap cache')
    marker = '    fun peek(styleId: String): SecureVector? = cache[assetIdFor(styleId)]'
    addition = '''    fun peekBitmap(styleId: String): ImageBitmap? = bitmapCache[assetIdFor(styleId)]

    fun loadBitmap(context: Context, styleId: String): ImageBitmap {
        require(styleId in V1_PUPPY_IDS) { "Original artwork requested for a non-V1 puppy" }
        val assetId = assetIdFor(styleId)
        bitmapCache[assetId]?.let { return it }
        val protectedBytes = context.assets.open("$PREFIX/$assetId.pup").use { it.readBytes() }
        val plain = decrypt(assetId, protectedBytes)
        require(plain.size >= 12 &&
            plain.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)) &&
            plain.copyOfRange(8, 12).contentEquals("WEBP".toByteArray(Charsets.US_ASCII))) {
            "Invalid original V1 puppy artwork: $assetId"
        }
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeByteArray(plain, 0, plain.size, options)
            ?: error("Unable to decode original V1 puppy artwork: $assetId")
        require(bitmap.width in 1..2048 && bitmap.height in 1..2048) {
            "Invalid original V1 artwork dimensions: $assetId"
        }
        val decoded = bitmap.asImageBitmap()
        return bitmapCache.putIfAbsent(assetId, decoded) ?: decoded
    }

'''
    return replace_once(text, marker, addition + marker, 'original artwork loader')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('archive', type=Path)
    args = parser.parse_args()
    if not GRADLE.is_file() or not RENDERER.is_file():
        parser.error('Run this script from the root of markhitchk/pup-clinker.')
    if not args.archive.is_file():
        parser.error('The approved artwork archive does not exist.')
    assets = validate_archive(args.archive)
    old_gradle = GRADLE.read_text()
    old_renderer = RENDERER.read_text()
    if 'protectedV1OriginalDir' in old_gradle:
        raise RuntimeError('The original V1 artwork pipeline is already installed.')
    new_gradle = patch_gradle(old_gradle)
    new_renderer = patch_renderer(old_renderer)
    target = APP / 'src/main/puppy-art/v1'
    target.mkdir(parents=True, exist_ok=True)
    for name, data in assets.items():
        (target / name).write_bytes(data)
    manifest = {'generation': 'v1', 'count': len(IDS), 'format': 'WebP', 'assets': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(assets.items())}}
    (target / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
    GRADLE.write_text(new_gradle)
    RENDERER.write_text(new_renderer)
    print('Imported 18 original V1 assets. V2, gameplay, save IDs and signing configuration are unchanged.')
    print('Run: ./gradlew :app:assembleDebug')


if __name__ == '__main__':
    main()
