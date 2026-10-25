// Run with Node.js and sharp available (for example via NODE_PATH).
// SVG is the source; PNGs are generated only for Android launcher resources.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');

const root = path.resolve(__dirname, '..');
const artwork = path.join(root, 'prototype/artwork');
const res = path.join(root, 'prototype/src/main/res');
const source = fs.readFileSync(path.join(artwork, 'app-icon.svg'), 'utf8');
const background = /<rect width="512" height="512" rx="116" fill="(#[A-Fa-f0-9]{6})"\s*\/>/;
const match = source.match(background);
if (!match) throw new Error('Expected one flat launcher background in app-icon.svg');
const foreground = source.replace(background, '')
  .replace('</defs>', '</defs><g transform="translate(256 256) scale(0.8) translate(-256 -256)">')
  .replace('</svg>', '</g></svg>');
const gesture = source.match(/<mask\b[^>]*id="swipe-silhouette"[^>]*>\s*<path d="([^"]+)"/);
if (!gesture) throw new Error('Missing swipe silhouette in app-icon.svg');
const stroke = gesture[1];
const whiteStroke = `<path d="${stroke}" fill="none" stroke="#FFFFFF" stroke-width="54" stroke-linecap="round"/>`;

function write(relative, content) {
  const target = path.join(res, relative);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, content + '\n');
}

async function main() {
  fs.writeFileSync(path.join(artwork, 'notification-icon.svg'),
    `<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="80 80 340 340" fill="none" role="img" aria-labelledby="title"><title id="title">Glide notification icon</title>${whiteStroke}</svg>\n`);
  for (const [density, scale] of Object.entries({ mdpi: 1, hdpi: 1.5, xhdpi: 2, xxhdpi: 3, xxxhdpi: 4 })) {
    const legacy = path.join(res, `mipmap-${density}`, 'ic_launcher.png');
    const layer = path.join(res, `drawable-${density}`, 'ic_launcher_foreground.png');
    fs.mkdirSync(path.dirname(legacy), { recursive: true });
    fs.mkdirSync(path.dirname(layer), { recursive: true });
    await sharp(Buffer.from(source)).resize(48 * scale, 48 * scale).png().toFile(legacy);
    await sharp(Buffer.from(foreground)).resize(108 * scale, 108 * scale).png().toFile(layer);
  }
  write('values/icon_colors.xml', `<resources><color name="ic_launcher_background">${match[1]}</color></resources>`);
  // Android 13 gets the monochrome layer from the v33 override.
  const adaptive = (monochrome) => `<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android"${monochrome ? '' : ' xmlns:tools="http://schemas.android.com/tools" tools:ignore="MonochromeLauncherIcon"'}>
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>${monochrome ? '\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>' : ''}
</adaptive-icon>`;
  write('mipmap-anydpi-v26/ic_launcher.xml', adaptive(false));
  write('mipmap-anydpi-v33/ic_launcher.xml', adaptive(true));
  const vectorPath = `<path android:fillColor="#00000000" android:strokeColor="#FFFFFF" android:strokeWidth="54" android:strokeLineCap="round" android:pathData="${stroke}"/>`;
  write('drawable/ic_launcher_monochrome.xml', `<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="512" android:viewportHeight="512"><group android:pivotX="256" android:pivotY="256" android:scaleX="0.8" android:scaleY="0.8">${vectorPath}</group></vector>`);
  // Prepared for future notifications; no notification behavior is enabled here.
  write('drawable/ic_stat_glide.xml', `<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools" tools:ignore="UnusedResources" android:width="24dp" android:height="24dp" android:viewportWidth="340" android:viewportHeight="340"><group android:translateX="-80" android:translateY="-80">${vectorPath}</group></vector>`);
  console.log('Generated launcher resources, themed icon, and notification SVG/vector.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
