#!/usr/bin/env python3
"""Create local, private whole-project data backups; rehearse restoration only into a fresh database."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile
import tempfile
import time
import uuid
from urllib.parse import urlparse

ROOT=Path(__file__).resolve().parent.parent

def validate_restore_name(name):
 if not re.fullmatch(r'elder_meal_restore_[A-Za-z0-9][A-Za-z0-9_]{0,60}',name):raise ValueError('恢复演练只允许新的 elder_meal_restore_ 前缀数据库')

def extract_checked(archive,destination):
 destination.mkdir(mode=0o700,parents=True,exist_ok=True)
 with tarfile.open(archive,'r:gz') as tar:
  for member in tar.getmembers():
   path=Path(member.name)
   if path.is_absolute() or '..' in path.parts or not (member.isfile() or member.isdir()):raise ValueError('备份包含不安全路径或链接')
  for member in tar.getmembers():
   target=destination/member.name
   if member.isdir():target.mkdir(mode=0o700,parents=True,exist_ok=True)
   else:
    target.parent.mkdir(mode=0o700,parents=True,exist_ok=True)
    with tar.extractfile(member) as source,target.open('xb') as output:shutil.copyfileobj(source,output)
    target.chmod(0o600)
 verify_files(destination)

def digest(path):
 h=hashlib.sha256()
 with path.open('rb') as stream:
  for chunk in iter(lambda:stream.read(1024*1024),b''):h.update(chunk)
 return h.hexdigest()

def verify_files(path):
 manifest=json.loads((path/'manifest.json').read_text())
 for relative,expected in manifest['files'].items():
  file=Path(relative)
  if file.is_absolute() or '..' in file.parts:raise ValueError('校验清单路径无效')
  if not (path/file).is_file() or digest(path/file)!=expected:raise ValueError('备份校验和不一致')
 if not {'database.sql','server.env'}.issubset(manifest['files']):raise ValueError('缺少数据库或配置密钥')
 return manifest

def connection():
 password=os.environ.get('DB_PASSWORD')
 if not password:raise ValueError('请在 .env 配置数据库凭据')
 parsed=urlparse(os.environ.get('DB_URL','jdbc:mysql://127.0.0.1:3306/elder_meal').removeprefix('jdbc:'))
 name=parsed.path.strip('/') or 'elder_meal'
 if not re.fullmatch(r'[A-Za-z0-9_]+',name):raise ValueError('数据库名称无效')
 env=os.environ.copy();env['MYSQL_PWD']=password
 return ['-h',parsed.hostname or '127.0.0.1','-P',str(parsed.port or 3306),'-u',os.environ.get('DB_USER','root')],env,name

def command(binary,args,env,**kwargs):
 result=subprocess.run([binary,*args],env=env,stderr=subprocess.PIPE,**kwargs)
 if result.returncode:raise ValueError(binary+' 执行失败，请检查数据库权限和连接；未输出敏感配置')
 return result

SUMMARY="SELECT 'orders',COUNT(*) FROM meal_order UNION ALL SELECT 'successful_payments',COUNT(*) FROM payment_record WHERE status='SUCCESS' UNION ALL SELECT 'successful_refunds',COUNT(*) FROM refund_record WHERE status='REFUNDED' UNION ALL SELECT 'wallet_ledger',COUNT(*) FROM wallet_ledger UNION ALL SELECT 'files',COUNT(*) FROM stored_file UNION ALL SELECT 'paid_amount',COALESCE(SUM(amount),0) FROM payment_record WHERE status='SUCCESS' UNION ALL SELECT 'refund_amount',COALESCE(SUM(amount),0) FROM refund_record WHERE status='REFUNDED' UNION ALL SELECT 'subsidy_net',COALESCE(SUM(amount),0) FROM subsidy_ledger UNION ALL SELECT 'wallet_balances',COALESCE(SUM(balance),0) FROM wallet_account UNION ALL SELECT 'migration_version',MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL"

def summary(args,env,name):
 result=command('mysql',[*args,'-N','-B',name,'-e',SUMMARY],env,stdout=subprocess.PIPE)
 return dict(line.split('\t') for line in result.stdout.decode().splitlines())

def create(destination):
 args,env,name=connection();destination=destination.resolve();destination.mkdir(mode=0o700,parents=True,exist_ok=True)
 archive=destination/('elder-meal-'+time.strftime('%Y%m%d-%H%M%S')+'-'+uuid.uuid4().hex[:6]+'.tar.gz')
 with tempfile.TemporaryDirectory(prefix='meal-backup-') as temporary:
  root=Path(temporary);before=summary(args,env,name)
  with (root/'database.sql').open('wb') as output:command('mysqldump',[*args,'--single-transaction','--routines','--triggers','--no-tablespaces',name],env,stdout=output)
  if before!=summary(args,env,name):raise ValueError('备份期间业务数据发生变化，请暂停写入后重试')
  if not re.fullmatch(r'[A-Fa-f0-9]{64}',os.environ.get('APP_ENCRYPTION_KEY','')):raise ValueError('缺少稳定的 64 位十六进制解密密钥')
  settings=ROOT/'.env'
  if not settings.is_file():raise ValueError('缺少需要独立保护的 .env 配置')
  shutil.copyfile(settings,root/'server.env');(root/'server.env').chmod(0o600)
  upload=Path(os.environ.get('APP_UPLOAD_PATH','uploads/private'));upload=upload if upload.is_absolute() else ROOT/upload
  if upload.exists():
   if upload.is_symlink() or any(p.is_symlink() for p in upload.rglob('*')):raise ValueError('附件目录包含链接，请使用实际私有目录')
   shutil.copytree(upload,root/'private')
  else:(root/'private').mkdir()
  manifest={'format':1,'createdAt':time.strftime('%Y-%m-%dT%H:%M:%S%z'),'summary':before,'files':{str(p.relative_to(root)):digest(p) for p in root.rglob('*') if p.is_file()}}
  (root/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
  try:
   fd=os.open(archive,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
   with os.fdopen(fd,'wb') as output:
    with tarfile.open(fileobj=output,mode='w:gz') as tar:
     for path in root.iterdir():tar.add(path,arcname=path.name)
  except Exception:
   archive.unlink(missing_ok=True);raise
 print('项目数据备份已生成（含数据库、私有附件、配置与解密密钥；仅当前用户可读）：'+str(archive))
 return archive

def verify(archive):
 args,env,_=connection();name='elder_meal_restore_'+time.strftime('%Y%m%d%H%M%S')+'_'+uuid.uuid4().hex[:8];validate_restore_name(name)
 with tempfile.TemporaryDirectory(prefix='meal-restore-') as temporary:
  root=Path(temporary);extract_checked(archive,root);manifest=verify_files(root)
  command('mysql',[*args,'-e',f'CREATE DATABASE `{name}` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci'],env,stdout=subprocess.PIPE)
  try:
   with (root/'database.sql').open('rb') as source:command('mysql',[*args,name],env,stdin=source,stdout=subprocess.PIPE)
   restored=summary(args,env,name)
   if {key:restored.get(key) for key in manifest['summary']}!=manifest['summary']:raise ValueError('恢复后的业务记录数不一致')
   checks=ROOT/'docs/reviews/wallet-integrity.sql'
   result=command('mysql',[*args,'-N','-B',name],env,input=checks.read_bytes(),stdout=subprocess.PIPE)
   if any(line.split('\t')[-1]!='0' for line in result.stdout.decode().splitlines()):raise ValueError('恢复后钱包对账不一致')
   print('恢复演练通过：校验和、附件、订单/支付/退款/钱包/文件记录、金额汇总、迁移版本及五项钱包对账均正确。')
   print(json.dumps(restored,ensure_ascii=False));print('演练仅使用独立临时数据库，完成后自动删除，当前项目数据未覆盖。')
  finally:
   validate_restore_name(name);command('mysql',[*args,'-e',f'DROP DATABASE `{name}`'],env,stdout=subprocess.PIPE)

def main():
 parser=argparse.ArgumentParser(description=__doc__);sub=parser.add_subparsers(dest='action',required=True)
 create_parser=sub.add_parser('create');create_parser.add_argument('destination',nargs='?',type=Path,default=ROOT/'backups')
 verify_parser=sub.add_parser('verify');verify_parser.add_argument('archive',type=Path)
 options=parser.parse_args()
 try:
  if options.action=='create':create(options.destination)
  else:verify(options.archive)
 except (ValueError,OSError,tarfile.TarError,KeyError) as failure:parser.exit(1,'备份操作失败：'+str(failure)+'\n')
if __name__=='__main__':main()
