// Mirror App/.../LegacyPuppyTransparency.kt for the original V1/V2 PNGs.
// Generate *derived* Metro assets. Original Kotlin and React source PNGs are immutable.
import { readFile, writeFile, mkdir, stat } from 'node:fs/promises';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

const mobile = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const originalMap = join(mobile, 'src/assets/puppies.js');
const derivedMap = join(mobile, 'src/assets/puppies-transparent.js');
const generatedRoot = join(mobile, 'assets/generated');
const isMatte = (data, pixel) => data[pixel] <= 20 && data[pixel + 1] <= 20 && data[pixel + 2] <= 20;

/** 4-connected dark matte removal, identical threshold and traversal to Kotlin.
 * Works on decoded RGBA and changes only pixels reachable from an image edge.
 */
export function stripEdgeMatte(png) {
  const { width, height, data } = png;
  if (!width || !height || data.length !== width * height * 4)
    throw new Error('Invalid decoded PNG dimensions');
  const queue = new Int32Array(width * height);
  let head = 0, tail = 0;
  const enqueue = (index) => {
    const offset = index * 4;
    if (data[offset + 3] === 0 || !isMatte(data, offset)) return;
    data[offset + 3] = 0;
    queue[tail++] = index;
  };
  for (let x = 0; x < width; x++) {
    enqueue(x);
    if (height > 1) enqueue((height - 1) * width + x);
  }
  for (let y = 1; y < height - 1; y++) {
    enqueue(y * width);
    if (width > 1) enqueue(y * width + width - 1);
  }
  while (head < tail) {
    const index = queue[head++];
    const x = index % width, y = (index - x) / width;
    if (x > 0) enqueue(index - 1);
    if (x + 1 < width) enqueue(index + 1);
    if (y > 0) enqueue(index - width);
    if (y + 1 < height) enqueue(index + width);
  }
  return tail;
}

/** Kotlin checks Bitmap.hasAlpha(); original opaque truecolor PNGs (type 2)
 * need conversion, while alpha-enabled art must be preserved byte-for-byte.
 */
export function shouldRemoveMatte(bytes) {
  return bytes.subarray(0, 8).equals(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])) &&
    bytes[24] === 8 && bytes[25] === 2 && bytes[28] === 0;
}

const pngToBuffer = png => PNG.sync.write(png, { colorType: 6, inputColorType: 6 });

// Preserve source icon artwork, but normalize its visible nontransparent bounds
// into the Android adaptive-icon safe zone. Kotlin uses the unmodified logo for
// its legacy launcher icon; this foreground is ONLY for masked Android launchers.
export function adaptiveForeground(source, size = 1024) {
  const out = new PNG({ width: size, height: size, colorType: 6 });
  let minX = source.width, minY = source.height, maxX = -1, maxY = -1;
  for (let y = 0; y < source.height; y++)
    for (let x = 0; x < source.width; x++) {
      if (source.data[(y * source.width + x) * 4 + 3] >= 20) {
        minX = Math.min(minX, x); minY = Math.min(minY, y);
        maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
      }
    }
  if (maxX < 0 || maxY < 0) throw new Error('Original brand logo is empty');
  const bboxW = maxX - minX + 1, bboxH = maxY - minY + 1;
  const scale = (size * 0.65) / Math.max(bboxW, bboxH);
  const outputW = Math.max(1, Math.round(bboxW * scale));
  const outputH = Math.max(1, Math.round(bboxH * scale));
  const startX = Math.floor((size - outputW) / 2);
  const startY = Math.floor((size - outputH) / 2);
  // Bilinear interpolation avoids the jagged icon edges produced by pixel-nearest enlargement.
  for (let y = 0; y < outputH; y++) {
    const sy = Math.min(source.height - 1, minY + (y + .5) / scale - .5);
    const y0 = Math.max(0, Math.floor(sy)), y1 = Math.min(source.height - 1, y0 + 1);
    const fy = Math.min(1, Math.max(0, sy - y0));
    for (let x = 0; x < outputW; x++) {
      const sx = Math.min(source.width - 1, minX + (x + .5) / scale - .5);
      const x0 = Math.max(0, Math.floor(sx)), x1 = Math.min(source.width - 1, x0 + 1);
      const fx = Math.min(1, Math.max(0, sx - x0));
      const dest = ((startY + y) * size + startX + x) * 4;
      for (let c = 0; c < 4; c++) {
        const a = source.data[(y0 * source.width + x0) * 4 + c];
        const b = source.data[(y0 * source.width + x1) * 4 + c];
        const d = source.data[(y1 * source.width + x0) * 4 + c];
        const e = source.data[(y1 * source.width + x1) * 4 + c];
        out.data[dest + c] = Math.round(
          (a * (1 - fx) + b * fx) * (1 - fy) +
          (d * (1 - fx) + e * fx) * fy
        );
      }
    }
  }
  return out;
}

export async function prepareLegacyArt() {
  const content = await readFile(originalMap, 'utf8');
  const entries = [...content.matchAll(/([a-zA-Z][\w]*):require\('\.\.\/\.\.\/assets\/(v1|v2)\/([^']+\.png)'\)/g)];
  if (entries.length < 20) throw new Error('Expected at least 20 original Kotlin puppy PNG references');
  const prepared = [];
  let converted = 0;
  for (const [, id, folder, filename] of entries) {
    const src = join(mobile, 'assets', folder, filename);
    const bytes = await readFile(src);
    if (!shouldRemoveMatte(bytes)) continue;
    const target = join(generatedRoot, 'puppies', folder, filename);
    await mkdir(dirname(target), { recursive: true });
    const decoded = PNG.sync.read(bytes);
    const affected = stripEdgeMatte(decoded);
    if (!affected) continue;
    await writeFile(target, pngToBuffer(decoded));
    converted++;
    prepared.push(`  ${JSON.stringify(id)}: require('../../assets/generated/puppies/${folder}/${filename}')`);
  }
  const map = '// GENERATED. Original images remain at assets/v1 and assets/v2.\n' +
    'export const MATTE_FREE_PUPPY_IMAGES = {\n' + prepared.join(',\n') + '\n};\n';
  await writeFile(derivedMap, map);
  const logoPath = join(mobile, 'assets/logos/puppy_clicker.png');
  const logo = PNG.sync.read(await readFile(logoPath));
  await mkdir(join(generatedRoot, 'branding'), { recursive: true });
  await writeFile(join(generatedRoot, 'branding/launcher-adaptive.png'),
    pngToBuffer(adaptiveForeground(logo)));
  if (!converted) throw new Error('No opaque V1/V2 image had an edge-connected black matte');
  console.log(`Kotlin PNG transparency: prepared ${converted} images; adaptive foreground generated from original logo.`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  await prepareLegacyArt();
}
