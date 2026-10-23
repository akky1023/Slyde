import unittest
import io
import json
import pathlib
import tempfile
from unittest.mock import patch
import capture
from capture import parse_axes, decode_touch, ensure_device


class ConnectionTests(unittest.TestCase):
    def test_connection_states(self):
        class DeviceList:
            def __init__(self, listing):
                self.listing = listing
            def run(self, *args):
                return self.listing
        for listing, message in (
            ('List of devices attached\n', '見つかりません'),
            ('List of devices attached\na unauthorized\n', '未承認'),
            ('List of devices attached\na offline\n', '利用可能な状態'),
            ('List of devices attached\na device\nb device\n', '複数'),
        ):
            with self.subTest(listing=listing), self.assertRaisesRegex(RuntimeError, message):
                ensure_device(DeviceList(listing))
        ensure_device(DeviceList('List of devices attached\na device\nb device\n'), 'b')


class TouchTests(unittest.TestCase):
    def test_axes_and_multitouch_lifecycle(self):
        axes = parse_axes('''add device 1: /dev/input/event4
 ABS_MT_POSITION_X : value 0, min 0, max 2000, fuzz 0
 ABS_MT_POSITION_Y : value 0, min 100, max 4100, fuzz 0''')
        lines = '''[ 1.000] /dev/input/event4: EV_ABS ABS_MT_TRACKING_ID 00000002
[ 1.001] /dev/input/event4: EV_ABS ABS_MT_POSITION_X 000003e8
[ 1.002] /dev/input/event4: EV_ABS ABS_MT_POSITION_Y 00000834
[ 1.003] /dev/input/event4: EV_SYN SYN_REPORT 00000000
[ 1.004] /dev/input/event4: EV_ABS ABS_MT_SLOT 00000001
[ 1.005] /dev/input/event4: EV_ABS ABS_MT_TRACKING_ID 00000003
[ 1.006] /dev/input/event4: EV_ABS ABS_MT_POSITION_X 000007d0
[ 1.007] /dev/input/event4: EV_ABS ABS_MT_POSITION_Y 00001004
[ 1.008] /dev/input/event4: EV_SYN SYN_REPORT 00000000
[ 1.009] /dev/input/event4: EV_ABS ABS_MT_SLOT 00000000
[ 1.010] /dev/input/event4: EV_ABS ABS_MT_TRACKING_ID ffffffff
[ 1.011] /dev/input/event4: EV_SYN SYN_REPORT 00000000'''
        frames = list(decode_touch(lines.splitlines(), axes))
        self.assertEqual(frames[0]['pointers'][0]['normalizedX'], .5)
        self.assertEqual(frames[0]['pointers'][0]['normalizedY'], .5)
        self.assertEqual(len(frames[1]['pointers']), 2)
        self.assertEqual(frames[2]['ended'][0]['trackingId'], 2)
        self.assertEqual(frames[2]['pointers'][0]['normalizedY'], 1)

    def test_slot_reuse_does_not_reuse_coordinates(self):
        lines = '''[ 1] /dev/input/event0: EV_ABS ABS_MT_TRACKING_ID 00000001
[ 1] /dev/input/event0: EV_ABS ABS_MT_POSITION_X 00000004
[ 1] /dev/input/event0: EV_SYN SYN_REPORT 00000000
[ 2] /dev/input/event0: EV_ABS ABS_MT_TRACKING_ID ffffffff
[ 2] /dev/input/event0: EV_SYN SYN_REPORT 00000000
[ 3] /dev/input/event0: EV_ABS ABS_MT_TRACKING_ID 00000002
[ 3] /dev/input/event0: EV_SYN SYN_REPORT 00000000'''
        frames = list(decode_touch(lines.splitlines(), {}))
        self.assertNotIn('rawX', frames[-1]['pointers'][0])

    def test_overrun_marks_data_loss_and_unknown_axes_stay_raw(self):
        lines = '''[ 1] /dev/input/event0: EV_ABS ABS_MT_TRACKING_ID 00000001
[ 1] /dev/input/event0: EV_ABS ABS_MT_POSITION_X 00000004
[ 1] /dev/input/event0: EV_SYN SYN_REPORT 00000000
[ 2] /dev/input/event0: EV_SYN SYN_DROPPED 00000000
[ 2] /dev/input/event0: EV_ABS ABS_MT_POSITION_X 00000008
[ 2] /dev/input/event0: EV_SYN SYN_REPORT 00000000
[ 3] /dev/input/event0: EV_SYN SYN_REPORT 00000000'''
        frames = list(decode_touch(lines.splitlines(), {}))
        self.assertEqual(len(frames), 2)
        self.assertEqual(frames[-1]['type'], 'dropped')
        self.assertNotIn('normalizedX', frames[0]['pointers'][0])


class CaptureLifecycleTests(unittest.TestCase):
    def test_video_stop_collection_and_setting_restore_without_raw_permission(self):
        instances = []

        class FakeProcess:
            def __init__(self, raw=False):
                self.raw = raw
                self.returncode = 1 if raw else None
                self.stdout = io.StringIO('RECORDER_PID:123\n')

            def poll(self):
                return self.returncode

            def communicate(self, timeout=None):
                self.returncode = 0
                return '', ''

            def terminate(self):
                self.returncode = 0

            def wait(self, timeout=None):
                return self.returncode

        class FakeAdb:
            def __init__(self, *args):
                self.calls = []
                self.video = None
                instances.append(self)

            def run(self, *args, **kwargs):
                self.calls.append(args)
                if args == ('devices', '-l'):
                    return 'List of devices attached\ntest device model:Test\n'
                if args == ('get-state',):
                    return 'device'
                if args[:4] == ('shell', 'settings', 'get', 'system'):
                    return '0'
                if args == ('shell', 'cat', '/proc/uptime'):
                    return '123.40 42.00'
                if args[:3] == ('exec-out', 'screencap', '-p'):
                    return b'\x89PNG\r\n\x1a\n'
                if args[:2] == ('shell', 'kill'):
                    self.video.returncode = 0
                if args[0] == 'pull':
                    pathlib.Path(args[2]).write_bytes(b'test-video')
                if args == ('shell', 'run-as', capture.PACKAGE, 'ls', 'files/sessions'):
                    return 'abc-def.jsonl'
                if args[:4] == ('exec-out', 'run-as', capture.PACKAGE, 'cat'):
                    return b'{"type":"caseEnd"}\n'
                return ''

            def popen(self, *args, **kwargs):
                if args[:3] == ('shell', 'getevent', '-lt'):
                    kwargs['stdout'].write(b'Permission denied\n')
                    return FakeProcess(raw=True)
                self.video = FakeProcess()
                return self.video

        with tempfile.TemporaryDirectory(dir=pathlib.Path(__file__).resolve().parent) as directory, patch.object(capture, 'Adb', FakeAdb), \
             patch('sys.argv', ['capture.py', '--output', directory, '--duration', '.01']), \
             patch('builtins.print'):
            capture.main()
            folder = next(pathlib.Path(directory).iterdir())
            data = json.loads((folder / 'metadata.json').read_text(encoding='utf-8'))
            self.assertEqual(data['sessionFileCount'], 1)
            self.assertEqual(data['touchFrameCount'], 0)
            self.assertTrue(data['segments'][0]['saved'])
            self.assertFalse(data['errors'])
            self.assertEqual((folder / 'screen-001.mp4').read_bytes(), b'test-video')
        calls = instances[0].calls
        self.assertIn(('shell', 'kill', '-2', '123'), calls)
        self.assertIn(('shell', 'settings', 'put', 'system', 'pointer_location', '0'), calls)
        self.assertIn(('shell', 'settings', 'put', 'system', 'show_touches', '0'), calls)

    def test_failure_before_recording_restores_settings(self):
        calls = []

        class FailingAdb:
            def __init__(self, *args):
                pass

            def run(self, *args, **kwargs):
                calls.append(args)
                if args == ('devices', '-l'):
                    return 'List of devices attached\ntest device model:Test\n'
                if args == ('get-state',):
                    return 'device'
                if args[:4] == ('shell', 'settings', 'get', 'system'):
                    return 'null'
                if args[:2] == ('shell', 'getprop'):
                    raise RuntimeError('disconnected')
                return ''

        with tempfile.TemporaryDirectory(dir=pathlib.Path(__file__).resolve().parent) as directory, patch.object(capture, 'Adb', FailingAdb), \
             patch('sys.argv', ['capture.py', '--output', directory]), patch('builtins.print'):
            with self.assertRaisesRegex(RuntimeError, 'disconnected'):
                capture.main()
            folder = next(pathlib.Path(directory).iterdir())
            self.assertTrue((folder / 'metadata.json').exists())
        self.assertIn(('shell', 'settings', 'delete', 'system', 'pointer_location'), calls)
        self.assertIn(('shell', 'settings', 'delete', 'system', 'show_touches'), calls)


if __name__ == '__main__':
    unittest.main()
