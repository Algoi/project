import importlib.util
import io
import json
from pathlib import Path
import tarfile
import tempfile
import unittest

spec=importlib.util.spec_from_file_location('backup_manager',Path(__file__).with_name('backup-manager.py'))
backup=importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)

class BackupSafetyTest(unittest.TestCase):
 def test_live_database_name_is_never_allowed_as_restore_target(self):
  for name in ['elder_meal','mysql','elder_meal_restore_../../x','elder_meal_restore_']:
   with self.assertRaises(ValueError):backup.validate_restore_name(name)
  backup.validate_restore_name('elder_meal_restore_20261017_abcd')
 def test_archive_cannot_escape_restore_directory(self):
  with tempfile.TemporaryDirectory() as root:
   archive=Path(root)/'bad.tar.gz'
   with tarfile.open(archive,'w:gz') as tar:
    row=tarfile.TarInfo('../outside');row.size=1;tar.addfile(row,io.BytesIO(b'x'))
   with self.assertRaises(ValueError):backup.extract_checked(archive,Path(root)/'out')
 def test_checksum_failure_is_reported_before_restore(self):
  with tempfile.TemporaryDirectory() as root:
   path=Path(root);(path/'database.sql').write_text('changed');(path/'manifest.json').write_text(json.dumps({'files':{'database.sql':'wrong'}}))
   with self.assertRaises(ValueError):backup.verify_files(path)

if __name__=='__main__':unittest.main()
