#!/usr/bin/env python3
"""Local Linux lifecycle manager. Runtime metadata and logs live under .run/."""
import argparse
import contextlib
from dataclasses import dataclass
import fcntl
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request


class ManagerError(RuntimeError):
    pass


@dataclass(frozen=True)
class Service:
    name: str
    label: str
    port: int
    command: tuple | None
    path: str
    codes: tuple
    markers: tuple


def identity(pid):
    try:
        stat = Path(f"/proc/{pid}/stat").read_text().rsplit(")", 1)[1].split()
        if stat[0] == "Z":
            return None
        return {"pid": pid, "start": stat[19], "boot": Path("/proc/sys/kernel/random/boot_id").read_text().strip()}
    except (FileNotFoundError, ProcessLookupError, PermissionError):
        return None


def current(record):
    return bool(record) and identity(record["pid"]) == record


def wait_for(predicate, timeout):
    deadline = time.monotonic() + timeout
    while not predicate():
        if time.monotonic() >= deadline:
            raise ManagerError("等待服务就绪超时")
        time.sleep(0.2)


class Manager:
    def __init__(self, root, services, timeout=180, stop_timeout=30):
        self.root = Path(root).resolve()
        self.services = services
        self.runtime = self.root / ".run"
        self.runtime.mkdir(mode=0o700, exist_ok=True)
        self.timeout = timeout
        self.stop_timeout = stop_timeout
        self.children = {}
        # Ignore ambient HTTP proxies when probing local processes.
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    @contextlib.contextmanager
    def exclusive(self):
        with (self.runtime / "service.lock").open("a") as lock:
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError:
                raise ManagerError("另一个启停命令正在执行，请稍后再试") from None
            yield

    def state(self):
        try:
            state = json.loads((self.runtime / "services.json").read_text())
            if not isinstance(state, dict):
                raise ValueError()
            for entry in state.values():
                record = entry["identity"]
                if not isinstance(record["pid"], int) or not all(key in record for key in ("start", "boot")):
                    raise ValueError()
            return state
        except FileNotFoundError:
            return {}
        except (ValueError, KeyError, TypeError):
            raise ManagerError(".run/services.json 状态文件无效，请移走该文件后重试；服务可按端口重新识别") from None

    def save(self, state):
        temporary = self.runtime / "services.json.tmp"
        temporary.write_text(json.dumps(state, indent=2) + "\n")
        temporary.replace(self.runtime / "services.json")

    def belongs(self, service, pid):
        try:
            directory = Path(os.readlink(f"/proc/{pid}/cwd")).resolve()
            command = Path(f"/proc/{pid}/cmdline").read_bytes().replace(b"\0", b" ").decode(errors="replace")
            return (directory == self.root or self.root in directory.parents) and any(marker in command for marker in service.markers)
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            return False

    def listeners(self, service):
        result = subprocess.run(["lsof", "-t", "-nP", f"-iTCP:{service.port}", "-sTCP:LISTEN"], capture_output=True, text=True)
        return {int(pid) for pid in result.stdout.split() if pid.isdigit()}

    def owned(self, service):
        return {pid for pid in self.listeners(service) if self.belongs(service, pid)}

    @staticmethod
    def occupied(service):
        with socket.socket() as connection:
            connection.settimeout(0.3)
            return connection.connect_ex(("127.0.0.1", service.port)) == 0

    def ready(self, service):
        try:
            with self.http.open(f"http://127.0.0.1:{service.port}{service.path}", timeout=1) as response:
                return response.status in service.codes
        except urllib.error.HTTPError as error:
            accepted = error.code in service.codes
            error.close()
            return accepted
        except (urllib.error.URLError, OSError, TimeoutError):
            return False

    def start(self):
        state = self.state()
        required = [service for service in self.services if service.command]
        # Check all required ports before launching any process.
        for service in required:
            if self.occupied(service) and not self.owned(service):
                raise ManagerError(f"端口 {service.port} 被其他程序占用，未启动或关闭任何新服务")
        created = []
        try:
            for service in required:
                owned = self.owned(service)
                record = state.get(service.name)
                if owned:
                    if not record or not current(record["identity"]):
                        state[service.name] = {"identity": identity(min(owned)), "mode": "adopted"}
                    print(f"{service.label}：已运行，继续使用（{service.port}）", flush=True)
                elif record and current(record["identity"]):
                    print(f"{service.label}：等待已有启动进程", flush=True)
                else:
                    environment = os.environ.copy()
                    backend = next((s for s in self.services if s.name == "backend"), None)
                    if backend:
                        environment["API_PROXY_TARGET"] = f"http://127.0.0.1:{backend.port}"
                    with (self.runtime / f"{service.name}.log").open("ab") as log:
                        process = subprocess.Popen(service.command, cwd=self.root, env=environment,
                            stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
                            start_new_session=True, close_fds=True)
                    self.children[service.name] = process
                    record = identity(process.pid)
                    if record is None:
                        process.wait(timeout=5)
                        raise ManagerError(f"{service.label}启动失败，请查看 .run/{service.name}.log")
                    state[service.name] = {"identity": record, "mode": "group"}
                    created.append(service.name)
                    print(f"{service.label}：正在后台启动，日志 .run/{service.name}.log", flush=True)
                self.save(state)

            def all_ready():
                complete = True
                for service in required:
                    if self.owned(service) and self.ready(service):
                        continue
                    record = state[service.name]
                    process = self.children.get(service.name)
                    if not current(record["identity"]) or process is not None and process.poll() is not None:
                        raise ManagerError(f"{service.label}启动失败，请查看 .run/{service.name}.log")
                    complete = False
                return complete

            wait_for(all_ready, self.timeout)
            web = next((service for service in required if service.name == "web"), required[0])
            print(f"项目整体已就绪：http://127.0.0.1:{web.port}", flush=True)
        except BaseException:
            self._stop(created)
            raise

    def _stop(self, names):
        state = self.state()
        targets = []
        groups = set()
        for service in self.services:
            if service.name not in names:
                continue
            entry = state.get(service.name)
            if entry and current(entry["identity"]):
                pid = entry["identity"]["pid"]
                if entry["mode"] == "group":
                    try:
                        if os.getpgid(pid) == pid:
                            groups.add(pid)
                        targets.append(entry["identity"])
                    except ProcessLookupError:
                        pass
                elif self.belongs(service, pid):
                    targets.append(entry["identity"])
            for pid in self.owned(service):
                record = identity(pid)
                if record:
                    targets.append(record)
            state.pop(service.name, None)
        for group in groups:
            try:
                os.killpg(group, signal.SIGTERM)
            except ProcessLookupError:
                pass
        for record in targets:
            if current(record):
                try:
                    if os.getpgid(record["pid"]) not in groups:
                        os.kill(record["pid"], signal.SIGTERM)
                except ProcessLookupError:
                    pass
        deadline = time.monotonic() + self.stop_timeout
        while any(current(record) for record in targets) and time.monotonic() < deadline:
            time.sleep(0.1)
        for record in targets:
            if current(record):
                try:
                    if record["pid"] in groups:
                        os.killpg(record["pid"], signal.SIGKILL)
                    else:
                        os.kill(record["pid"], signal.SIGKILL)
                except ProcessLookupError:
                    pass
        for name in names:
            process = self.children.pop(name, None)
            if process:
                process.wait(timeout=5)
        self.save(state)

    def stop(self):
        self._stop([service.name for service in self.services])
        print("本项目服务已停止；MySQL 继续由系统管理。", flush=True)

    def restart(self):
        self.stop()
        self.start()

    def status(self):
        state = self.state()
        healthy = True
        active = False
        conflict = False
        for service in self.services:
            owned = self.owned(service)
            running = bool(owned) and self.ready(service)
            record = state.get(service.name)
            if running:
                message = f"运行中，PID {','.join(map(str, sorted(owned)))}"
            elif self.occupied(service) and not owned:
                message = "端口被其他程序占用"
            elif owned or record and current(record["identity"]):
                message = "进程存在，尚未就绪"
            else:
                message = "未运行"
            print(f"{service.label} ({service.port})：{message}")
            active = active or bool(owned) or bool(record and current(record["identity"]))
            if service.command:
                conflict = conflict or (self.occupied(service) and not owned)
                if not running:
                    healthy = False
        overall = "运行中" if healthy else "异常（端口占用）" if conflict else "部分运行（尚未就绪）" if active else "已停止"
        print(f"项目整体：{overall}")
        return healthy

    def logs(self, name):
        names = [service.name for service in self.services] if name is None else [name]
        for service_name in names:
            file = self.runtime / f"{service_name}.log"
            print(f"--- {file.relative_to(self.root)} ---")
            print("\n".join(file.read_text(errors="replace").splitlines()[-60:]) if file.exists() else "尚无托管启动日志")


def main():
    parser = argparse.ArgumentParser(prog="./scripts/service.sh", description="统一管理整个本地助餐项目的启动、停止和状态")
    parser.add_argument("command", nargs="?", default="status", choices=("start", "stop", "restart", "status", "logs"))
    parser.add_argument("--backend-port", type=int, default=8080, help=argparse.SUPPRESS)
    parser.add_argument("--timeout", type=int, default=180, help="启动等待秒数，默认 180")
    args = parser.parse_args()
    if not 0 < args.backend_port < 65536 or args.backend_port in (5173, 4173) or args.timeout <= 0:
        parser.error("端口应有效且不能与前端端口相同，等待秒数必须大于 0")
    root = Path(__file__).resolve().parent.parent
    services = [
        Service("backend", "后端", args.backend_port, (str(root / "scripts/dev-server.sh"),),
                "/api/v1/auth/me", (401,), ("com.community.meal.MealApplication", "elder-meal-server-1.0.0.jar")),
        Service("web", "前端", 5173, ("npm", "--prefix", str(root / "web"), "run", "dev"), "/", (200,), ("vite",)),
        Service("preview", "预览（可选）", 4173, None, "/", (200,), ("vite preview",)),
    ]
    os.umask(0o077)
    manager = Manager(root, services, args.timeout)
    try:
        if args.command == "status":
            return 0 if manager.status() else 3
        if args.command == "logs":
            manager.logs(None)
            return 0
        with manager.exclusive():
            getattr(manager, args.command)()
        return 0
    except (ManagerError, OSError, subprocess.SubprocessError) as error:
        print(f"操作失败：{error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("已取消；本次新启动的进程已清理。", file=sys.stderr)
        return 130


if __name__ == "__main__":
    sys.exit(main())
