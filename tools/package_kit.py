"""Bundle the debug APK, recorder, instructions and reproducible sources."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED


def main():
    root = Path(__file__).resolve().parent.parent
    apk = root / 'dist/arte-input-recorder-debug.apk'
    if not apk.is_file():
        raise SystemExit('先に scripts/build-recorder.ps1 を実行してください')
    paths = [root / name for name in (
        'README.md', 'PLAN.md', 'start-recording.cmd', 'build.gradle', 'settings.gradle',
        'gradle.properties', '.gitignore', 'gradlew', 'gradlew.bat', 'recorder/build.gradle')]
    for directory in ('gradle/wrapper', 'recorder/src', 'input-core/src', 'sumire-engine/src', 'prototype/src', 'docs'):
        paths.extend(path for path in (root / directory).rglob('*') if path.is_file())
    paths.extend((root / 'scripts').glob('*.ps1'))
    paths.extend((root / 'tools').glob('*.py'))
    for module in ('input-core', 'sumire-engine', 'prototype'):
        paths.append(root / module / 'build.gradle')
    paths.extend(path for path in (root / 'sumire-engine').glob('*') if path.is_file() and path.name != 'build.gradle')
    paths.append(apk)
    output = root / 'dist/arte-input-recording-kit.zip'
    with ZipFile(output, 'w', ZIP_DEFLATED) as archive:
        for path in sorted(paths):
            name = path.relative_to(root).as_posix()
            if path.suffix.lower() == '.ps1':
                # Windows PowerShell 5.1 treats UTF-8 without BOM as the ANSI code page.
                archive.writestr(name, path.read_text(encoding='utf-8-sig').encode('utf-8-sig'))
            else:
                archive.write(path, name)
    print(output)


if __name__ == '__main__':
    main()
