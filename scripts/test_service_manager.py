"""Lifecycle regressions use temporary HTTP servers; they never touch project ports or MySQL."""
import contextlib
import io
import importlib.util
import socket
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("service_manager", Path(__file__).with_name("service-manager.py"))
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)


class ServiceManagerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name) / "project"
        self.root.mkdir()
        self.port = self.free_port()
        self.service = module.Service("web", "前端", self.port,
            (sys.executable, "-m", "http.server", str(self.port), "--bind", "127.0.0.1"), "/", (200,), ("http.server",))
        self.manager = module.Manager(self.root, [self.service], timeout=5, stop_timeout=3)
        self.addCleanup(self.manager.stop)

    @staticmethod
    def free_port():
        with socket.socket() as connection:
            connection.bind(("127.0.0.1", 0))
            return connection.getsockname()[1]

    def server(self, directory):
        process = subprocess.Popen(self.service.command, cwd=directory, stdout=subprocess.DEVNULL,
                                   stderr=subprocess.DEVNULL, start_new_session=True)
        self.addCleanup(lambda: (process.terminate(), process.wait(timeout=5)) if process.poll() is None else None)
        module.wait_for(lambda: self.manager.ready(self.service), 5)
        return process

    def test_start_is_idempotent_and_stop_cleans_up(self):
        self.manager.start()
        identity = self.manager.state()["web"]["identity"]
        self.manager.start()
        self.assertEqual(identity, self.manager.state()["web"]["identity"])
        self.assertTrue(self.manager.status())
        self.manager.stop()
        self.assertFalse(self.manager.ready(self.service))
        self.assertFalse(self.manager.status())
        self.manager.stop()

    def test_existing_project_service_is_adopted_and_stopped(self):
        process = self.server(self.root)
        self.manager.start()
        self.assertEqual(process.pid, self.manager.state()["web"]["identity"]["pid"])
        self.manager.stop()
        process.wait(timeout=5)

    def test_foreign_port_is_never_adopted_or_stopped(self):
        foreign = Path(self.temp.name) / "foreign"
        foreign.mkdir()
        process = self.server(foreign)
        with self.assertRaises(module.ManagerError):
            self.manager.start()
        self.assertFalse(self.manager.status())
        self.manager.stop()
        self.assertIsNone(process.poll())

    def test_start_failure_rolls_back_only_new_processes(self):
        bad = module.Service("bad", "失败服务", self.free_port(),
                             (sys.executable, "-c", "raise SystemExit(2)"), "/", (200,), ("failure-fixture",))
        manager = module.Manager(self.root, [self.service, bad], timeout=5, stop_timeout=3)
        with self.assertRaises(module.ManagerError):
            manager.start()
        self.assertFalse(self.manager.ready(self.service))
        self.assertEqual({}, manager.state())

    def test_start_failure_preserves_preexisting_service(self):
        process = self.server(self.root)
        bad = module.Service("bad", "失败服务", self.free_port(),
                             (sys.executable, "-c", "raise SystemExit(2)"), "/", (200,), ("failure-fixture",))
        manager = module.Manager(self.root, [self.service, bad], timeout=5, stop_timeout=3)
        with self.assertRaises(module.ManagerError):
            manager.start()
        self.assertIsNone(process.poll())
        self.assertTrue(self.manager.ready(self.service))

    def second_service(self):
        port = self.free_port()
        return module.Service("other", "另一组件", port,
            (sys.executable, "-m", "http.server", str(port), "--bind", "127.0.0.1"), "/", (200,), ("http.server",))

    def test_project_status_distinguishes_partial_from_stopped(self):
        manager = module.Manager(self.root, [self.service, self.second_service()], timeout=5, stop_timeout=3)
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertFalse(manager.status())
        self.assertIn("项目整体：已停止", output.getvalue())
        self.server(self.root)
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertFalse(manager.status())
        self.assertIn("项目整体：部分运行", output.getvalue())

    def test_preview_alone_is_reported_as_partial_project(self):
        self.server(self.root)
        required = self.second_service()
        optional = module.Service(self.service.name, "预览", self.port, None, "/", (200,), ("http.server",))
        manager = module.Manager(self.root, [required, optional], timeout=5, stop_timeout=3)
        with contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertFalse(manager.status())
        self.assertIn("项目整体：部分运行", output.getvalue())

    def test_restart_replaces_all_project_components(self):
        manager = module.Manager(self.root, [self.service, self.second_service()], timeout=5, stop_timeout=3)
        self.addCleanup(manager.stop)
        manager.start()
        before = {name: entry["identity"] for name, entry in manager.state().items()}
        manager.restart()
        after = {name: entry["identity"] for name, entry in manager.state().items()}
        self.assertEqual(set(before), set(after))
        self.assertTrue(all(before[name] != after[name] for name in before))
        self.assertTrue(manager.status())

    def test_stale_pid_record_cannot_stop_an_unrelated_process(self):
        foreign = subprocess.Popen([sys.executable, "-c", "import time;time.sleep(60)"], start_new_session=True)
        self.addCleanup(lambda: (foreign.terminate(), foreign.wait(timeout=5)) if foreign.poll() is None else None)
        identity = module.identity(foreign.pid)
        identity["boot"] = "previous-boot"
        self.manager.save({"web": {"identity": identity, "mode": "group"}})
        self.manager.stop()
        self.assertIsNone(foreign.poll())


if __name__ == "__main__":
    unittest.main()
