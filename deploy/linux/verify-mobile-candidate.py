import hashlib,json,pathlib,sys,tarfile
def digest(p):
 h=hashlib.sha256()
 with open(p,'rb') as stream:
  for block in iter(lambda:stream.read(1024*1024),b''):h.update(block)
 return h.hexdigest()
def verify(root,front=None,stage=True):
 root=pathlib.Path(root);m=json.loads((root/'artifact-manifest.json').read_text())
 assert m['release']=='20261017-mobile-agent-v23' and m['schemaVersion']==23
 for name,value in m['artifacts'].items():
  assert name in {'fitness-backend.jar','frontend-dist.tar.gz'} and digest(root/name)==value
 with tarfile.open(root/'frontend-dist.tar.gz','r:gz') as archive:
  seen=set()
  for member in archive.getmembers():
   p=pathlib.PurePosixPath(member.name)
   assert not p.is_absolute() and '..' not in p.parts and member.isfile()
   assert member.name in m['frontendFiles'] and member.name not in seen
   assert hashlib.sha256(archive.extractfile(member).read()).hexdigest()==m['frontendFiles'][member.name]
   seen.add(member.name)
  assert seen==set(m['frontendFiles'])
 if stage:
  passed=json.loads((root/'stage-passed.json').read_text())
  assert passed['status']=='passed' and passed['artifacts']==m['artifacts']
  assert passed['environmentSha256']==digest(root/'candidate.env')
  assert set(passed['verifiedRoles'])=={'SUPER_ADMIN','MANAGER','RECEPTION','COACH','MEMBER','SALES'}
 if front:
  front=pathlib.Path(front)
  assert {p.relative_to(front).as_posix() for p in front.rglob('*') if p.is_file()}==set(m['frontendFiles'])
  for name,value in m['frontendFiles'].items():assert digest(front/name)==value
 return m
if __name__=='__main__':
 verify(sys.argv[1],sys.argv[2] if len(sys.argv)>2 else None)
 print('Candidate artifacts, rehearsal marker and frontend checksums verified')
