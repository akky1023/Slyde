"""USB recorder for the companion debug APK. Python 3.10+, standard library only."""
from __future__ import annotations

import argparse
import json
import pathlib
import re
import os
import subprocess
import threading
import time
from datetime import datetime

PACKAGE = "net.neomenia.inputrecorder.debug"


class Adb:
    def __init__(self, executable="adb", serial=None):
        self.command = [executable] + (["-s", serial] if serial else [])

    def run(self, *args, check=True, binary=False, timeout=30):
        result = subprocess.run(self.command + list(args), capture_output=True, timeout=timeout)
        if check and result.returncode:
            detail = result.stderr.decode("utf-8", "replace") or result.stdout.decode("utf-8", "replace")
            raise RuntimeError(f"ADB実行失敗 ({result.returncode}): {subprocess.list2cmdline(self.command + list(args))}\n{detail.strip()}")
        return result.stdout if binary else result.stdout.decode("utf-8", "replace").strip()

    def popen(self, *args, **kwargs):
        if os.name == "nt":
            kwargs.setdefault("creationflags", subprocess.CREATE_NEW_PROCESS_GROUP)
        return subprocess.Popen(self.command + list(args), **kwargs)


def parse_axes(capabilities):
    """Preserve each input device's own ranges, never assume display pixels."""
    devices = {}
    current = None
    for line in capabilities.splitlines():
        found = re.search(r"add device .*?:\s*(/dev/input/event\d+)", line)
        if found:
            current = found[1]
            devices[current] = {}
        axis = re.search(r"(ABS_MT_POSITION_[XY]).*?min\s+(-?\d+),\s*max\s+(-?\d+)", line)
        if current and axis:
            devices[current][axis[1]] = {"min": int(axis[2]), "max": int(axis[3])}
    return devices


EVENT = re.compile(r"\[\s*([\d.]+)\]\s+(/dev/input/event\d+):\s+(\w+)\s+(\w+)\s+([0-9a-fA-F]+)")


def decode_touch(lines, axes):
    """Decode Linux type-B multitouch; raw is authoritative, normalized is panel-relative."""
    devices = {}
    for line in lines:
        match = EVENT.search(line)
        if not match:
            continue
        stamp, device, kind, code, value = match.groups()
        state = devices.setdefault(device, {"slot": 0, "slots": {}, "ended": [], "dropped": False})
        number = int(value, 16)
        if number >= 2**31:
            number -= 2**32
        if code == "SYN_DROPPED":
            state["dropped"] = True
            state["slots"].clear()
            yield {"timeSeconds": float(stamp), "device": device, "type": "dropped"}
        elif kind == "EV_ABS" and code == "ABS_MT_SLOT":
            state["slot"] = number
        elif kind == "EV_ABS" and not state["dropped"]:
            slot = state["slot"]
            point = state["slots"].setdefault(slot, {})
            if code == "ABS_MT_TRACKING_ID":
                if number < 0:
                    state["ended"].append({"slot": slot, **point})
                    state["slots"].pop(slot, None)
                else:
                    state["slots"][slot] = {"trackingId": number}
            elif code == "ABS_MT_POSITION_X":
                point["rawX"] = number
            elif code == "ABS_MT_POSITION_Y":
                point["rawY"] = number
            elif code == "ABS_MT_PRESSURE":
                point["pressure"] = number
        elif code == "SYN_REPORT":
            if state["dropped"]:
                # Cannot reconstruct existing fingers after a kernel overrun. Resume at new IDs.
                state["dropped"] = False
                state["ended"].clear()
                continue
            points = []
            for slot, point in state["slots"].items():
                if "trackingId" not in point:
                    continue
                item = {"slot": slot, **point}
                for coord, axis in (("X", "ABS_MT_POSITION_X"), ("Y", "ABS_MT_POSITION_Y")):
                    limits = axes.get(device, {}).get(axis)
                    if limits and "raw" + coord in point and limits["max"] > limits["min"]:
                        item["normalized" + coord] = (point["raw" + coord] - limits["min"]) / (limits["max"] - limits["min"])
                points.append(item)
            if points or state["ended"]:
                yield {"timeSeconds": float(stamp), "device": device, "type": "frame",
                       "pointers": points, "ended": state["ended"]}
            state["ended"] = []


def write_json(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


def screenshot(adb, path):
    image = adb.run("exec-out", "screencap", "-p", binary=True)
    if not image.startswith(b"\x89PNG"):
        raise RuntimeError("スクリーンショットを取得できません")
    path.write_bytes(image)


def clock_sample(adb):
    before = time.time_ns()
    uptime = adb.run("shell", "cat", "/proc/uptime")
    return {"hostBeforeNs": before, "deviceProcUptime": uptime, "hostAfterNs": time.time_ns(),
            "note": "proc uptime includes suspend; app uptimeMs/getevent exclude suspend. Keep device awake."}


def collect(adb, destination):
    destination.mkdir(exist_ok=True)
    names = adb.run("shell", "run-as", PACKAGE, "ls", "files/sessions", check=False)
    count = 0
    for name in names.splitlines():
        if not re.fullmatch(r"[0-9a-f-]+\.jsonl", name.strip()):
            continue
        content = adb.run("exec-out", "run-as", PACKAGE, "cat", "files/sessions/" + name, binary=True)
        (destination / name).write_bytes(content)
        count += 1
    return count


def ensure_device(adb, serial=None):
    listing = adb.run('devices', '-l')
    devices = []
    for line in listing.splitlines():
        fields = line.split()
        if len(fields) >= 2 and not line.startswith(('List of devices', '*')):
            devices.append((fields[0], fields[1]))
    if serial:
        devices = [entry for entry in devices if entry[0] == serial]
    if not devices:
        raise RuntimeError('USBデバッグ接続の端末が見つかりません。\nGalaxy S25をデータ通信可能なUSBケーブルで接続し、開発者向けオプションの「USBデバッグ」をONにしてください。\n端末に確認画面が出たら「このパソコンからのUSBデバッグを許可」を選び、もう一度起動してください。')
    if len(devices) > 1:
        raise RuntimeError('複数の端末があります。record.ps1 -Serial 端末番号 で指定してください。\n' + listing)
    if devices[0][1] == 'unauthorized':
        raise RuntimeError('端末がUSBデバッグを未承認です。端末をロック解除し、このPCからの接続を許可してください。\n' + listing)
    if devices[0][1] != 'device':
        raise RuntimeError('端末が利用可能な状態ではありません。USBを接続し直してください。\n' + listing)


def main():
    parser = argparse.ArgumentParser(description="アルテ入力の録画・タッチ軌跡・編集履歴をUSB経由で保存")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", help="複数端末が接続されている場合のシリアル")
    parser.add_argument("--output", type=pathlib.Path, default=pathlib.Path("captures"))
    parser.add_argument("--apk", type=pathlib.Path, help="指定時だけ記録アプリをインストール／更新")
    parser.add_argument("--collect-only", action="store_true", help="前回のアプリ記録だけ回収")
    parser.add_argument("--duration", type=float, help="指定秒数で自動終了。通常はCtrl+Cで終了")
    args = parser.parse_args()
    if args.duration is not None and args.duration <= 0:
        parser.error("--duration は正の秒数で指定してください")
    adb = Adb(args.adb, args.serial)
    ensure_device(adb, args.serial)
    if adb.run("get-state") != "device":
        raise RuntimeError("USBデバッグで接続・承認した端末が必要です")
    if args.apk:
        print(adb.run("install", "-r", str(args.apk.resolve()), timeout=120))
    # Verify debug package access before modifying settings or launching capture.
    adb.run("shell", "run-as", PACKAGE, "pwd")
    folder = args.output.resolve() / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    folder.mkdir(parents=True)
    if args.collect_only:
        print(f"{collect(adb, folder / 'sessions')} 件を回収: {folder}")
        return

    old_pointer = adb.run("shell", "settings", "get", "system", "pointer_location")
    old_taps = adb.run("shell", "settings", "get", "system", "show_touches")
    stopped = threading.Event()
    errors = []
    segments = []
    video_thread = None
    raw_process = None
    raw_file = None
    metadata = {"schema": 1, "startedAt": datetime.now().astimezone().isoformat(),
                "package": PACKAGE, "segments": segments, "errors": errors,
                "previousSettings": {"pointer_location": old_pointer, "show_touches": old_taps},
                "coordinates": "raw touchscreen axes + normalized panel coordinates; do not assume video pixels",
                "videoTiming": "host intervals bracket launch/exit, not exact frame PTS synchronization"}

    def videos():
        index = 0
        while not stopped.is_set():
            index += 1
            remote = f"/data/local/tmp/arte-recorder-{folder.name}-{index:03}.mp4"
            item = {"file": f"screen-{index:03}.mp4", "hostLaunchNs": time.time_ns(), "remote": remote}
            process = None
            try:
                item["clockBefore"] = clock_sample(adb)
                # Default native capture size; no fixed downscale. 170 sec avoids screenrecord's 180 sec limit.
                process = adb.popen("shell", f"screenrecord --bit-rate 12000000 --time-limit 170 {remote} & p=$!; echo RECORDER_PID:$p; wait $p",
                                    stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                first = process.stdout.readline().strip()
                found = re.fullmatch(r"RECORDER_PID:(\d+)", first)
                if not found:
                    raise RuntimeError("screenrecord PIDを取得できません: " + first)
                pid = found[1]
                item["pid"] = pid
                segments.append(item)
                write_json(folder / "metadata.json", metadata)
                while process.poll() is None and not stopped.wait(0.25):
                    pass
                if process.poll() is None:
                    # SIGINT finalizes MP4; kill only this capture's screenrecord PID.
                    adb.run("shell", "kill", "-2", pid, check=False)
                _, stderr = process.communicate(timeout=20)
                item["hostEndNs"] = time.time_ns()
                item["returnCode"] = process.returncode
                item["stderr"] = stderr
                item["clockAfter"] = clock_sample(adb)
                pulled = adb.run("pull", remote, str(folder / item["file"]), check=False)
                item["pullOutput"] = pulled
                local = folder / item["file"]
                item["saved"] = local.exists() and local.stat().st_size > 0
                if item["saved"]:
                    adb.run("shell", "rm", remote, check=False)
                if not item["saved"]:
                    raise RuntimeError("録画を回収できません。端末側ファイル: " + remote)
                if item["hostEndNs"] - item["hostLaunchNs"] < 2_000_000_000 and not stopped.is_set():
                    raise RuntimeError("録画が短時間で終了しました: " + stderr)
                if process.returncode not in (0, 130) and not stopped.is_set():
                    raise RuntimeError("録画が異常終了しました: " + stderr)
                write_json(folder / "metadata.json", metadata)
            except Exception as error:
                errors.append("video: " + str(error))
                print("録画エラー: " + str(error), flush=True)
                stopped.set()
                if process and process.poll() is None:
                    process.terminate()
                return

    try:
        metadata["device"] = adb.run("shell", "getprop", "ro.product.model")
        metadata["android"] = adb.run("shell", "getprop", "ro.build.version.release")
        metadata["wmSize"] = adb.run("shell", "wm", "size")
        metadata["wmDensity"] = adb.run("shell", "wm", "density")
        metadata["initialClock"] = clock_sample(adb)
        (folder / "display.txt").write_text(adb.run("shell", "dumpsys", "display"), encoding="utf-8")
        capabilities = adb.run("shell", "getevent", "-lp", check=False)
        (folder / "input-devices.txt").write_text(capabilities, encoding="utf-8")
        metadata["axes"] = parse_axes(capabilities)
        adb.run("shell", "settings", "put", "system", "pointer_location", "1")
        adb.run("shell", "settings", "put", "system", "show_touches", "1")
        metadata["pointerOverlayEnabled"] = adb.run("shell", "settings", "get", "system", "pointer_location") == "1"
        if not metadata["pointerOverlayEnabled"]:
            print("警告: ポインタ位置の表示を有効にできていません。端末の開発者向けオプションを確認してください。", flush=True)
        adb.run("shell", "am", "start", "-n", PACKAGE + "/net.neomenia.inputrecorder.RecorderActivity")
        time.sleep(1)
        screenshot(adb, folder / "screen-start.png")
        raw_file = (folder / "getevent.txt").open("wb")
        raw_process = adb.popen("shell", "getevent", "-lt", stdout=raw_file, stderr=subprocess.STDOUT)
        video_thread = threading.Thread(target=videos, daemon=True)
        video_thread.start()
        print(f"保存先: {folder}\nアプリで例題を選び「記録開始」を押して、アルテで入力してください。", flush=True)
        print("終了時はアプリの「記録停止」を押してから、この画面で Ctrl+C。録画は170秒ごとに分割します。", flush=True)
        deadline = time.monotonic() + args.duration if args.duration is not None else None
        while not stopped.wait(1):
            if deadline is not None and time.monotonic() >= deadline:
                break
            if raw_process.poll() is not None and "rawTouchStatus" not in metadata:
                metadata["rawTouchStatus"] = "unavailable_or_stopped"
                print("生のタッチ取得が終了しました。録画と軌跡表示で記録を継続します。", flush=True)
    except KeyboardInterrupt:
        print("保存中…", flush=True)
    finally:
        stopped.set()
        if video_thread:
            video_thread.join(timeout=55)
            if video_thread.is_alive():
                errors.append("録画終了を待機中。metadata.jsonの端末側パスに未回収ファイルがある可能性があります")
        if raw_process:
            raw_process.terminate()
            try:
                raw_process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                raw_process.kill()
        if raw_file:
            raw_file.close()
        for setting, old in (("pointer_location", old_pointer), ("show_touches", old_taps)):
            try:
                if old == "null":
                    adb.run("shell", "settings", "delete", "system", setting)
                else:
                    adb.run("shell", "settings", "put", "system", setting, old)
            except Exception as error:
                errors.append(f"設定の復元に失敗 ({setting}): {error}")
        try:
            metadata["sessionFileCount"] = collect(adb, folder / "sessions")
            screenshot(adb, folder / "screen-end.png")
            metadata["finalClock"] = clock_sample(adb)
        except Exception as error:
            errors.append("回収: " + str(error))
        raw_path = folder / "getevent.txt"
        if raw_path.exists():
            with (folder / "touches.jsonl").open("w", encoding="utf-8") as output:
                frames = 0
                with raw_path.open(encoding="utf-8", errors="replace") as source:
                    for event in decode_touch(source, metadata.get("axes", {})):
                        output.write(json.dumps(event, ensure_ascii=False) + "\n")
                        frames += 1
            metadata["touchFrameCount"] = frames
            metadata["rawTouchStatus"] = "recorded" if frames else "no_touch_frames_check_getevent_txt"
        metadata["endedAt"] = datetime.now().astimezone().isoformat()
        write_json(folder / "metadata.json", metadata)
        print(f"保存完了: {folder}", flush=True)
        if not metadata.get("touchFrameCount"):
            print("数値の軌跡は取得できていません。録画に軌跡が映っているか確認してください。", flush=True)
        if errors:
            print("注意: " + " / ".join(errors), flush=True)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        raise SystemExit(str(error))
