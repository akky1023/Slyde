"""Exercise the shipped launcher with Windows PowerShell 5.1, without touching a device."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parent.parent
POWERSHELL = Path(os.environ.get('SystemRoot', 'C:/Windows')) / 'System32/WindowsPowerShell/v1.0/powershell.exe'


@unittest.skipUnless(os.name == 'nt' and POWERSHELL.is_file(), 'Windows PowerShell 5.1 required')
class LauncherTests(unittest.TestCase):
    def test_sources_parse_with_windows_powershell(self):
        command = r"$ErrorActionPreference='Stop'; Get-ChildItem -LiteralPath $args[0] -Filter *.ps1 | ForEach-Object { $t=$null; $e=$null; [System.Management.Automation.Language.Parser]::ParseFile($_.FullName,[ref]$t,[ref]$e) > $null; if ($e.Count) { throw ($e | Out-String) } }"
        # Pass the path as an environment value so command quoting cannot alter it.
        command = command.replace('$args[0]', '$env:RECORDER_TEST_SCRIPTS')
        env = dict(os.environ, RECORDER_TEST_SCRIPTS=str(ROOT / 'scripts'))
        result = subprocess.run([str(POWERSHELL), '-NoProfile', '-Command', command], env=env, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stderr.decode(errors='replace'))

    def test_packaged_launcher_executes_from_japanese_path(self):
        subprocess.run([sys.executable, str(ROOT / 'tools/package_kit.py')], check=True, capture_output=True)
        with tempfile.TemporaryDirectory(dir=ROOT / 'tools') as temp:
            kit = Path(temp) / '日本語 キット'
            with ZipFile(ROOT / 'dist/arte-input-recording-kit.zip') as archive:
                for name in archive.namelist():
                    if name.endswith('.ps1'):
                        self.assertTrue(archive.read(name).startswith(b'\xef\xbb\xbf'), name)
                archive.extractall(kit)
            # Stub only the capture entry point, so this test never starts ADB or recording.
            (kit / 'tools/capture.py').write_text('import json, sys\nprint(json.dumps(sys.argv[1:]))\n', encoding='utf-8')
            result = subprocess.run([str(POWERSHELL), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                str(kit / 'scripts/record.ps1'), '-Python', sys.executable, '-Adb', 'adb with spaces',
                '-Serial', 'test-serial', '-Install', '-CollectOnly'], capture_output=True)
            self.assertEqual(result.returncode, 0, result.stderr.decode(errors='replace'))
            args = next(json.loads(line) for line in result.stdout.decode(errors='replace').splitlines() if line.startswith('['))
            self.assertEqual(args[args.index('--adb') + 1], 'adb with spaces')
            self.assertEqual(args[args.index('--serial') + 1], 'test-serial')
            self.assertEqual(Path(args[args.index('--apk') + 1]), kit / 'dist/arte-input-recorder-debug.apk')
            self.assertIn('--collect-only', args)

    def test_python_stderr_is_saved_and_failure_status_preserved(self):
        with tempfile.TemporaryDirectory(dir=ROOT / 'tools') as temp:
            kit = Path(temp) / 'エラーログ テスト'
            with ZipFile(ROOT / 'dist/arte-input-recording-kit.zip') as archive:
                archive.extractall(kit)
            (kit / 'tools/capture.py').write_text("import sys\nprint('端末がUSBデバッグを未承認です', file=sys.stderr)\nsys.exit(1)\n", encoding='utf-8')
            result = subprocess.run([str(POWERSHELL), '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
                str(kit / 'scripts/record.ps1'), '-Python', sys.executable], capture_output=True)
            self.assertEqual(result.returncode, 1)
            logs = list((kit / 'captures').glob('launcher-*.log'))
            self.assertEqual(len(logs), 1)
            self.assertIn('端末がUSBデバッグを未承認です', logs[0].read_text(encoding='utf-8-sig'))


if __name__ == '__main__':
    unittest.main()
